package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.AuthProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Dialog Auth Provider — integrates with Dialog's MIFE (My Information Front End)
 * authentication APIs.
 *
 * <p>Implements the canonical {@link AuthProvider} contract from
 * platform-common. Capabilities:</p>
 * <ul>
 *   <li>OTP send / verify (SMS-based passwordless login)</li>
 *   <li>Authorization-code exchange (MIFE OAuth2)</li>
 *   <li>Session validation / token introspection</li>
 *   <li>Refresh-token grant</li>
 * </ul>
 *
 * <p>Per-tenant config (MIFE base URL, client ID, client secret) is loaded
 * at runtime via {@link DialogHttpClient} which reads
 * {@code TenantConfigurationService} — NOT from env files.
 * Configure via Selfcare Studio admin: Integrations &gt; Dialog MIFE.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = AuthProvider.class)
@RequiredArgsConstructor
public class DialogAuthProvider implements ApiAdapter, AuthProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_MIFE";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // OTP
    // ================================================================

    @Override
    public OtpSendResult sendOtp(String tenantId, String msisdn, String channel) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("No Dialog MIFE integration configured for tenant")
                    .build();
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn);
        body.put("channel", channel != null ? channel : "SMS");
        body.put("clientId", cfg.clientId() != null ? cfg.clientId() : "");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = httpClient.post(tenantId, cfg.baseUrl(),
                    "/auth/otp/send", body, token, null);
            return OtpSendResult.builder()
                    .success(true)
                    .correlationId(resp != null ? (String) resp.get("correlationId") : null)
                    .expiresInSeconds(300)
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog OTP send failed for {}: {}", msisdn, e.getMessage());
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("MIFE error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public OtpVerifyResult verifyOtp(String tenantId, String msisdn, String correlationId, String otpCode) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("No Dialog MIFE integration configured for tenant")
                    .build();
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn);
        body.put("correlationId", correlationId);
        body.put("otpCode", otpCode);
        body.put("clientId", cfg.clientId() != null ? cfg.clientId() : "");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = httpClient.post(tenantId, cfg.baseUrl(),
                    "/auth/otp/verify", body, token, null);
            if (resp == null) {
                return OtpVerifyResult.builder().success(false).failureReason("Empty MIFE response").build();
            }
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dm = (Map<String, Object>) d;
                return OtpVerifyResult.builder()
                        .success(true)
                        .connectionId((String) dm.get("subscriberId"))
                        .accessToken((String) dm.get("accessToken"))
                        .refreshToken((String) dm.get("refreshToken"))
                        .expiresInSeconds(3600)
                        .build();
            }
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("Invalid OTP")
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog OTP verify failed for {}: {}", msisdn, e.getMessage());
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("MIFE error: " + e.getMessage())
                    .build();
        }
    }

    // ================================================================
    // Session
    // ================================================================

    @Override
    public SessionValidationResult validateSession(String tenantId, String accessToken) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return SessionValidationResult.builder()
                    .active(false)
                    .failureReason("No Dialog MIFE integration configured for tenant")
                    .build();
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = httpClient.postForm(tenantId, cfg.baseUrl(),
                    "/oauth/introspect", form, token);
            if (resp == null) {
                return SessionValidationResult.builder().active(false).build();
            }
            boolean active = Boolean.TRUE.equals(resp.get("active"));
            return SessionValidationResult.builder()
                    .active(active)
                    .subscriberId((String) resp.get("sub"))
                    .scope((String) resp.get("scope"))
                    .expiresAt(resp.get("exp") instanceof Number n
                            ? new Date(n.longValue() * 1000L)
                            : null)
                    .build();
        } catch (DialogApiException e) {
            return SessionValidationResult.builder()
                    .active(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }

    @Override
    public TokenRefreshResult refreshToken(String tenantId, String refreshToken) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason("No Dialog MIFE integration configured for tenant")
                    .build();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        if (cfg.clientId() != null) form.add("client_id", cfg.clientId());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = httpClient.postForm(tenantId, cfg.baseUrl(), "/oauth/token", form, null);
            if (resp == null || resp.get("access_token") == null) {
                return TokenRefreshResult.builder().success(false).failureReason("Empty token response").build();
            }
            return TokenRefreshResult.builder()
                    .success(true)
                    .accessToken((String) resp.get("access_token"))
                    .refreshToken((String) resp.get("refresh_token"))
                    .expiresInSeconds(resp.get("expires_in") instanceof Number n ? n.intValue() : null)
                    .tokenType((String) resp.get("token_type"))
                    .build();
        } catch (DialogApiException e) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }

    // ================================================================
    // Authorization Code Exchange (MIFE OAuth2)
    // ================================================================

    /**
     * Exchange an authorization code for an access token (MIFE OAuth2).
     * Used by the MIFE redirect-based flow.
     */
    public TokenRefreshResult exchangeCode(String tenantId, String code, String redirectUri) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason("No Dialog MIFE integration configured for tenant")
                    .build();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        if (cfg.clientId() != null) form.add("client_id", cfg.clientId());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = httpClient.postForm(tenantId, cfg.baseUrl(), "/oauth/token", form, null);
            if (resp == null || resp.get("access_token") == null) {
                return TokenRefreshResult.builder().success(false).failureReason("Empty token response").build();
            }
            return TokenRefreshResult.builder()
                    .success(true)
                    .accessToken((String) resp.get("access_token"))
                    .refreshToken((String) resp.get("refresh_token"))
                    .expiresInSeconds(resp.get("expires_in") instanceof Number n ? n.intValue() : null)
                    .tokenType((String) resp.get("token_type"))
                    .build();
        } catch (DialogApiException e) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }
}
