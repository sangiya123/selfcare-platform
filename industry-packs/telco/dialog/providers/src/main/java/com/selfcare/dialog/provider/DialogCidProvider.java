package com.selfcare.dialog.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.CidAuthProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dialog CID (Customer Identity) provider — authorization-code login against MIFE.
 *
 * <p>Implements the canonical {@link CidAuthProvider} contract using Dialog's
 * CID identity chain:</p>
 *
 * <ul>
 *   <li>{@code /apicall/dio-token} — swap the authorization code for MIFE tokens
 *       and the encrypted {@code id_token}</li>
 *   <li>{@code .../profiles/get-by-cid} — fetch the customer profile (DDS)</li>
 *   <li>{@code .../wallets/get-by-cid-id} — best-effort wallet lookup (DDS)</li>
 * </ul>
 *
 * <p>This replicates the legacy {@code mda-auth-service} CID flow end-to-end so
 * {@code customer-identity-service} can issue platform JWTs whose claims mirror
 * the legacy response (subs, username, cxName, profile id/version, wallet id,
 * connection list, header-enrichment flag).</p>
 *
 * <p>Every API shape (path templates, redirect URI, field names) is resolved
 * from the tenant's DB-backed integration config via {@link DialogHttpClient}
 * — never hardcoded. Configure via Selfcare Studio admin: Integrations &gt;
 * Dialog MIFE, keys {@code path.cid.*}, {@code cid.redirectUri},
 * {@code field.cid.*}.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = CidAuthProvider.class)
@RequiredArgsConstructor
public class DialogCidProvider implements ApiAdapter, CidAuthProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_MIFE";
    private static final String ADAPTER_ID = "dialog-lk";

    private static final String DEFAULT_REDIRECT_URI = "net.omobio.dialogsc:/AuthDataReceiver";
    private static final String DEFAULT_TOKEN_PATH = "/apicall/dio-token";
    private static final String DEFAULT_PROFILE_PATH =
            "/apicall/crm/system/dds/selfcare-profile/v1.0.0/profiles/get-by-cid";
    private static final String DEFAULT_WALLET_PATH =
            "/apicall/crm/system/dds/get-wallet-by-cid/v1.0.0/wallets/get-by-cid-id";
    private static final String WALLET_API_ERROR = "API Error";

    private static final String GRANT_TYPE_AUTH_CODE = "authorization_code";

    private final DialogHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public CidExchangeResult exchangeCid(String tenantId, CidExchangeRequest request) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog MIFE not configured for tenant={}, cannot exchange CID", tenantId);
            return failure("No Dialog MIFE integration configured for tenant");
        }

        // 1. Exchange the authorization code for MIFE tokens + id_token
        String redirectUri = request.redirectUri() != null && !request.redirectUri().isBlank()
                ? request.redirectUri()
                : cfg.meta("cid.redirectUri", DEFAULT_REDIRECT_URI);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_AUTH_CODE);
        form.add("code", request.code());
        form.add("redirect_uri", redirectUri);
        if (cfg.clientId() != null) {
            form.add("client_id", cfg.clientId());
        }
        if (cfg.clientSecret() != null) {
            form.add("client_secret", cfg.clientSecret());
        }

        String tokenPath = cfg.meta("path.cid.token", DEFAULT_TOKEN_PATH);
        Map<String, Object> tokenResp;
        try {
            tokenResp = httpClient.postForm(tenantId, cfg.baseUrl(), tokenPath, form, null);
        } catch (DialogApiException e) {
            log.error("Dialog CID token exchange failed for tenant={}: {}", tenantId, e.getMessage());
            return failure(e.getMessage());
        }
        if (tokenResp == null) {
            return failure("Empty token response from Dialog MIFE");
        }

        String idToken = str(tokenResp.get("id_token"), null);
        if (idToken == null || idToken.isBlank()) {
            return failure("Missing id_token in Dialog MIFE token response");
        }
        String mifeAccessToken = str(tokenResp.get("access_token"), null);
        String mifeRefreshToken = str(tokenResp.get("refresh_token"), null);
        Integer expiresIn = intOf(tokenResp.get("expires_in"));

        // 2. Decode the id_token payload (username, sub, cx name, header flag)
        IdClaims claims;
        try {
            claims = decodeIdToken(idToken);
        } catch (IOException e) {
            log.warn("Invalid Dialog id_token for tenant={}: {}", tenantId, e.getMessage());
            return failure("Invalid id_token returned by Dialog MIFE");
        }
        if (claims.cidUuid() == null || claims.username() == null) {
            return failure("id_token missing required claims (sub/username)");
        }

        // 3. Fetch the customer profile (DDS get-by-cid)
        Map<String, Object> profile;
        try {
            profile = fetchProfile(tenantId, cfg, claims.cidUuid(), idToken);
        } catch (DialogApiException e) {
            log.error("Dialog CID profile lookup failed for tenant={} cid={}: {}",
                    tenantId, claims.cidUuid(), e.getMessage());
            return failure(e.getMessage());
        }
        if (profile == null) {
            return failure("Customer profile not found for the given CID");
        }

        String connField = cfg.mapping("cid.connectionList", "connection_list");
        String idField = cfg.mapping("cid.profileId", "id");
        String versionField = cfg.mapping("cid.profileVersion", "version");
        String imageField = cfg.mapping("cid.profileImage", "image_url");
        String walletField = cfg.mapping("cid.walletId", "wallet_id");

        // 4. Wallet lookup — best effort, matching legacy fallback to empty string
        String walletId = str(profile.get(walletField), null);
        if (walletId == null || walletId.isBlank() || WALLET_API_ERROR.equals(walletId)) {
            walletId = fetchWalletId(tenantId, cfg, claims.cidUuid());
        }

        log.info("Dialog CID exchanged for tenant={} cid={} username={} connections={}",
                tenantId, claims.cidUuid(), claims.username(),
                profile.get(connField) instanceof List<?> list ? list.size() : 0);

        return new CidExchangeResult(
                true,
                null,
                claims.username(),
                claims.cidUuid(),
                claims.cxName(),
                claims.headerEnriched(),
                mifeAccessToken,
                mifeRefreshToken,
                idToken,
                expiresIn,
                str(profile.get(idField), null),
                str(profile.get(versionField), null),
                str(profile.get(imageField), null),
                walletId,
                mapConnections(profile.get(connField))
        );
    }

    // ================================================================
    // MIFE calls
    // ================================================================

    private Map<String, Object> fetchProfile(String tenantId, DialogHttpClient.DialogConfig cfg,
                                             String cidUuid, String idToken) {
        String path = cfg.meta("path.cid.profile", DEFAULT_PROFILE_PATH);
        String appToken = httpClient.getAccessToken(
                tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("cid_uuid", cidUuid);
        query.put("login_id", idToken);
        query.put("traceId", UUID.randomUUID().toString());
        return httpClient.get(tenantId, cfg.baseUrl(), path, query, appToken, cfg.apiKey());
    }

    private String fetchWalletId(String tenantId, DialogHttpClient.DialogConfig cfg, String cidUuid) {
        String path = cfg.meta("path.cid.wallet", DEFAULT_WALLET_PATH);
        try {
            String appToken = httpClient.getAccessToken(
                    tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("cid_uuid", cidUuid);
            query.put("traceId", UUID.randomUUID().toString());
            Map<String, Object> data = httpClient.get(
                    tenantId, cfg.baseUrl(), path, query, appToken, cfg.apiKey());
            return data == null ? "" : str(data.get("wallet_id"), "");
        } catch (DialogApiException e) {
            log.warn("Dialog wallet lookup skipped for tenant={} cid={}: {}",
                    tenantId, cidUuid, e.getMessage());
            return "";
        }
    }

    // ================================================================
    // id_token decoding
    // ================================================================

    private IdClaims decodeIdToken(String idToken) throws IOException {
        String[] chunks = idToken.split("\\.");
        if (chunks.length < 2) {
            throw new IOException("Malformed id_token");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(padBase64(chunks[1]));
        JsonNode payload = objectMapper.readTree(decoded);
        String firstName = payload.path("first_name").asText("");
        String lastName = payload.path("last_name").asText("");
        String cxName = (firstName + " " + lastName).trim();
        return new IdClaims(
                payload.path("username").asText(null),
                payload.path("sub").asText(null),
                cxName,
                arrayContains(payload.path("amr"), "hdr")
        );
    }

    private String padBase64(String raw) {
        int rem = raw.length() % 4;
        if (rem == 0) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw);
        for (int i = rem; i < 4; i++) {
            sb.append('=');
        }
        return sb.toString();
    }

    private boolean arrayContains(JsonNode node, String value) {
        if (node == null || !node.isArray()) {
            return false;
        }
        for (JsonNode item : node) {
            if (value.equalsIgnoreCase(item.asText(""))) {
                return true;
            }
        }
        return false;
    }

    // ================================================================
    // Response mapping helpers
    // ================================================================

    private List<ConnectionDetail> mapConnections(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<ConnectionDetail> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(toConnection(map));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private ConnectionDetail toConnection(Map<?, ?> m) {
        Map<String, Object> f = (Map<String, Object>) m;
        return new ConnectionDetail(
                str(f.get("number"), null),
                str(f.get("name"), null),
                str(f.get("connection_id"), null),
                str(f.get("contract_id"), null),
                str(f.get("lob"), null),
                str(f.get("connection_type"), null),
                str(f.get("identification_type"), null),
                str(f.get("identification_number"), null),
                bool(f.get("is_dialog")),
                bool(f.get("is_primary")),
                str(f.get("created_date_time"), null),
                str(f.get("updated_date_time"), null),
                bool(f.get("is_hybrid")),
                str(f.get("cv_type"), null),
                bool(f.get("under_primary_nic")),
                str(f.get("care_of_nic"), null),
                bool(f.get("care_of_nic_validated")),
                bool(f.get("is_corporate")),
                str(f.get("cv_status"), null)
        );
    }

    private CidExchangeResult failure(String reason) {
        return new CidExchangeResult(false, reason, null, null, null, false,
                null, null, null, null, null, null, null, null, List.of());
    }

    private String str(Object v, String fallback) {
        if (v == null) {
            return fallback;
        }
        String s = v instanceof String ? v.toString() : String.valueOf(v);
        return s.isBlank() ? fallback : s;
    }

    private boolean bool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private Integer intOf(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================================================================
    // Internal types
    // ================================================================

    private record IdClaims(String username, String cidUuid, String cxName, boolean headerEnriched) {}
}