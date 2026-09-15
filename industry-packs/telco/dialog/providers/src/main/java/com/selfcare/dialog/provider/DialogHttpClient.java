package com.selfcare.dialog.provider;

import com.selfcare.platform.common.security.UrlAllowlist;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared HTTP client for Dialog provider beans.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Per-tenant OAuth2 {@code client_credentials} token cache with 5-minute
 *       proactive refresh window — used by all Dialog MIFE calls that need
 *       a bearer token.</li>
 *   <li>Convenience HTTP methods ({@code get}, {@code post}, {@code put}, {@code delete})
 *       that handle JSON encoding, idempotency headers, retries, and structured
 *       error mapping into {@link DialogApiException}.</li>
 *   <li>Config lookup helpers that resolve the tenant's base URL, client ID,
 *       client secret, and API key from
 *       {@link TenantConfigurationService} at request time.</li>
 * </ul>
 *
 * <p>All methods are tenant-aware — every request reads the per-tenant
 * integration config from MongoDB, not from environment variables.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialogHttpClient {

    private static final Duration TOKEN_REFRESH_BUFFER = Duration.ofMinutes(5);
    private static final int MAX_RETRIES = 2;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient.Builder restClientBuilder;
    private final TenantConfigurationService tenantConfig;

    // Per-tenant OAuth2 token cache: tenantId -> { token, expiresAt, baseUrl, clientId, clientSecret }
    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    // ================================================================
    // Config Resolution
    // ================================================================

    /**
     * Resolve the active Dialog MIFE integration config for a tenant.
     *
     * @return the config, or {@code null} when no ACTIVE integration is configured
     */
    public DialogConfig resolveConfig(String tenantId, String integrationType) {
        return tenantConfig.getIntegration(tenantId, integrationType)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> new DialogConfig(
                        tenantId,
                        c.getBaseUrl(),
                        c.getCredential("clientId"),
                        c.getCredential("clientSecret"),
                        c.getCredential("apiKey"),
                        c.getCredential("senderId"),
                        c.getMetadata(),
                        c.getFieldMapping()))
                .orElseGet(() -> {
                    log.warn("No active {} integration for tenant={}", integrationType, tenantId);
                    return DialogConfig.empty(tenantId);
                });
    }

    // ================================================================
    // Token Management (OAuth2 client_credentials)
    // ================================================================

    /**
     * Get a valid OAuth2 bearer token for the tenant. Cached, refreshed
     * 5 minutes before expiry.
     */
    public String getAccessToken(String tenantId, String baseUrl, String clientId, String clientSecret) {
        Objects.requireNonNull(tenantId, "tenantId");
        TokenEntry cached = tokenCache.get(tenantId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plus(TOKEN_REFRESH_BUFFER))) {
            return cached.token();
        }
        TokenEntry fetched = fetchNewToken(tenantId, baseUrl, clientId, clientSecret);
        tokenCache.put(tenantId, fetched);
        return fetched.token();
    }

    /**
     * Discard the cached token for a tenant. Called when the upstream API
     * rejects our token with 401 so the next call refreshes it.
     */
    public void invalidateToken(String tenantId) {
        tokenCache.remove(tenantId);
        log.debug("OAuth2 token invalidated for tenant={}", tenantId);
    }

    private TokenEntry fetchNewToken(String tenantId, String baseUrl, String clientId, String clientSecret) {
        String tokenUrl = baseUrl + "/oauth/token";
        log.debug("Fetching Dialog OAuth2 token for tenant={}", tenantId);

        RestClient client = buildClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (clientId != null && clientSecret != null) {
            headers.setBasicAuth(clientId, clientSecret);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "selfcare");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(tokenUrl)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new DialogApiException(null, DialogApiException.DIALOG_AUTH_001,
                        "Empty token response from Dialog MIFE");
            }

            String token = (String) response.get("access_token");
            Instant expiresAt = Instant.now().plus(tokenTtl(response));
            log.info("Dialog OAuth2 token obtained for tenant={}, expires_in={}",
                    tenantId, response.getOrDefault("expires_in", "unknown"));
            return new TokenEntry(token, expiresAt, baseUrl, clientId, clientSecret);
        } catch (HttpClientErrorException e) {
            log.error("Dialog token fetch failed for tenant={}: HTTP {} — {}",
                    tenantId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new DialogApiException(e.getStatusCode(), DialogApiException.DIALOG_AUTH_001,
                    "Failed to obtain Dialog token: " + e.getMessage(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Dialog OAuth2 endpoint error for tenant={}: {}",
                    tenantId, e.getMessage());
            throw new DialogApiException(null, DialogApiException.DIALOG_GEN_503,
                    "Dialog OAuth2 service unavailable", e);
        }
    }

    /**
     * Derive the token lifetime from the upstream {@code expires_in} field.
     * Falls back to 55 minutes when absent, unparsable, or implausibly short.
     */
    private Duration tokenTtl(Map<String, Object> response) {
        Object raw = response.get("expires_in");
        long seconds = 0L;
        if (raw instanceof Number n) {
            seconds = n.longValue();
        } else if (raw instanceof String s) {
            try {
                seconds = Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                log.warn("Dialog token response has non-numeric expires_in={}", raw);
            }
        }
        if (seconds < 60) {
            log.warn("Dialog token response has invalid expires_in={}; assuming 55m", raw);
            return Duration.ofMinutes(55);
        }
        return Duration.ofSeconds(seconds);
    }

    // ================================================================
    // HTTP Methods
    // ================================================================

    /**
     * GET request returning a parsed {@code Map} body, or {@code null} on 404.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String tenantId, String baseUrl, String path,
                                   Map<String, ? extends Object> queryParams,
                                   String bearerToken, String apiKey) {
        RestClient client = buildClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        String uri = buildUri(baseUrl + path, queryParams);
        HttpHeaders headers = authHeaders(bearerToken, apiKey);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<Map> resp = client.get()
                        .uri(uri)
                        .headers(h -> h.addAll(headers))
                        .retrieve()
                        .toEntity(Map.class);
                return handleResponse(resp);
            } catch (HttpClientErrorException.Unauthorized e) {
                if (attempt == 0 && bearerToken != null) {
                    invalidateToken(tenantId);
                    log.debug("Retrying GET {} after token refresh", path);
                    // Subsequent retry uses the refreshed token resolved lazily below
                    String refreshed = refreshCachedToken(tenantId);
                    if (refreshed != null) {
                        headers.setBearerAuth(refreshed);
                    }
                    continue;
                }
                throw toDialogException(e, path);
            } catch (HttpClientErrorException e) {
                throw toDialogException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new DialogApiException(e.getStatusCode(), DialogApiException.DIALOG_GEN_500,
                        "Dialog upstream error on GET " + path, e);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new DialogApiException(null, DialogApiException.DIALOG_GEN_503,
                        "Dialog upstream timeout on GET " + path, e);
            }
        }
        return null;
    }

    /**
     * POST request with a JSON body, returning a parsed {@code Map} body.
     */
    public Map<String, Object> post(String tenantId, String baseUrl, String path,
                                    Object body, String bearerToken, String apiKey) {
        return execute(HttpMethod.POST, tenantId, baseUrl, path, null, body, bearerToken, apiKey);
    }

    /**
     * POST request with form-encoded body (used for OAuth token exchange).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> postForm(String tenantId, String baseUrl, String path,
                                        MultiValueMap<String, String> formBody, String bearerToken) {
        RestClient client = buildClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (bearerToken != null) headers.setBearerAuth(bearerToken);

        try {
            ResponseEntity<Map> resp = client.post()
                    .uri(baseUrl + path)
                    .headers(h -> h.addAll(headers))
                    .body(formBody)
                    .retrieve()
                    .toEntity(Map.class);
            return handleResponse(resp);
        } catch (HttpClientErrorException e) {
            throw toDialogException(e, path);
        } catch (HttpServerErrorException e) {
            throw new DialogApiException(e.getStatusCode(), DialogApiException.DIALOG_GEN_500,
                    "Dialog upstream error on POST " + path, e);
        } catch (ResourceAccessException e) {
            throw new DialogApiException(null, DialogApiException.DIALOG_GEN_503,
                    "Dialog upstream timeout on POST " + path, e);
        }
    }

    /**
     * PUT request with a JSON body.
     */
    public Map<String, Object> put(String tenantId, String baseUrl, String path,
                                   Object body, String bearerToken, String apiKey) {
        return execute(HttpMethod.PUT, tenantId, baseUrl, path, null, body, bearerToken, apiKey);
    }

    /**
     * DELETE request. No response body.
     */
    public void delete(String tenantId, String baseUrl, String path,
                       String bearerToken, String apiKey) {
        RestClient client = buildClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        HttpHeaders headers = authHeaders(bearerToken, apiKey);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                client.delete()
                        .uri(baseUrl + path)
                        .headers(h -> h.addAll(headers))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException.Unauthorized e) {
                if (attempt == 0 && bearerToken != null) {
                    invalidateToken(tenantId);
                    String refreshed = refreshCachedToken(tenantId);
                    if (refreshed != null) {
                        headers.setBearerAuth(refreshed);
                    }
                    continue;
                }
                throw toDialogException(e, path);
            } catch (HttpClientErrorException e) {
                throw toDialogException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new DialogApiException(e.getStatusCode(), DialogApiException.DIALOG_GEN_500,
                        "Dialog upstream error on DELETE " + path, e);
            }
        }
    }

    // ================================================================
    // Internal Helpers
    // ================================================================

    private Map<String, Object> execute(HttpMethod method, String tenantId, String baseUrl, String path,
                                        Map<String, ? extends Object> queryParams,
                                        Object body, String bearerToken, String apiKey) {
        RestClient client = buildClient(baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        String uri = buildUri(baseUrl + path, queryParams);
        HttpHeaders headers = authHeaders(bearerToken, apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<Map> resp = client.method(method)
                        .uri(uri)
                        .headers(h -> h.addAll(headers))
                        .body(body)
                        .retrieve()
                        .toEntity(Map.class);
                return handleResponse(resp);
            } catch (HttpClientErrorException.Unauthorized e) {
                if (attempt == 0 && bearerToken != null) {
                    invalidateToken(tenantId);
                    String refreshed = refreshCachedToken(tenantId);
                    if (refreshed != null) {
                        headers.setBearerAuth(refreshed);
                    }
                    continue;
                }
                throw toDialogException(e, path);
            } catch (HttpClientErrorException e) {
                throw toDialogException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new DialogApiException(e.getStatusCode(), DialogApiException.DIALOG_GEN_500,
                        "Dialog upstream error on " + method + " " + path, e);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new DialogApiException(null, DialogApiException.DIALOG_GEN_503,
                        "Dialog upstream timeout on " + method + " " + path, e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleResponse(ResponseEntity<Map> resp) {
        if (resp.getStatusCode().is2xxSuccessful()) {
            return resp.getBody();
        }
        if (resp.getStatusCode().value() == 404) {
            return null;
        }
        Map<String, Object> body = resp.getBody();
        if (body != null && body.get("error") instanceof Map<?, ?> error) {
            String code = (String) ((Map<String, Object>) error).getOrDefault("code", DialogApiException.DIALOG_GEN_500);
            throw new DialogApiException(resp.getStatusCode(), code,
                    (String) ((Map<String, Object>) error).getOrDefault("message", "Unknown error"),
                    (Map<String, Object>) error);
        }
        throw new DialogApiException(resp.getStatusCode(), DialogApiException.DIALOG_GEN_500,
                "Unexpected Dialog response: " + resp.getStatusCode());
    }

    private String refreshCachedToken(String tenantId) {
        TokenEntry entry = tokenCache.get(tenantId);
        if (entry == null) return null;
        return getAccessToken(tenantId, entry.baseUrl(), entry.clientId(), entry.clientSecret());
    }

    private HttpHeaders authHeaders(String bearerToken, String apiKey) {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (bearerToken != null) h.setBearerAuth(bearerToken);
        if (apiKey != null) h.set("X-API-Key", apiKey);
        return h;
    }

    private String buildUri(String path, Map<String, ? extends Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return path;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        queryParams.forEach((k, v) -> {
            if (v != null) builder.queryParam(k, v);
        });
        return builder.build().toUriString();
    }

    private DialogApiException toDialogException(HttpClientErrorException e, String path) {
        String code = DialogApiException.DIALOG_GEN_500;
        Map<String, Object> payload = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = e.getResponseBodyAs(Map.class);
            if (body != null) {
                payload = body;
                if (body.get("error") instanceof Map<?, ?> error) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> em = (Map<String, Object>) error;
                    code = (String) em.getOrDefault("code", code);
                } else if (body.get("code") instanceof String s) {
                    code = s;
                }
            }
        } catch (Exception ignored) {
            // Body was not JSON; keep defaults
        }
        return new DialogApiException(e.getStatusCode(), code,
                String.format("Dialog API error on %s: %s", path, e.getMessage()), payload);
    }

    private boolean isTransient(HttpServerErrorException e) {
        int code = e.getStatusCode().value();
        return code == 502 || code == 503 || code == 504;
    }

    private void backoff(int attempt) {
        long millis = INITIAL_BACKOFF.toMillis() * (1L << attempt);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private RestClient buildClient(String baseUrl, Duration connect, Duration read) {
        enforceAllowlist(baseUrl);
        // Clone the shared builder so per-call baseUrl/requestFactory do not leak
        // into the singleton RestClient.Builder bean used by other services.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (connect != null) {
            factory.setConnectTimeout((int) connect.toMillis());
        }
        if (read != null) {
            factory.setReadTimeout((int) read.toMillis());
        }
        return restClientBuilder.clone()
                .requestFactory(factory)
                .baseUrl(baseUrl != null ? baseUrl : "")
                .build();
    }

    /**
     * Live SSRF enforcement: every outbound request to a Dialog endpoint is
     * checked against the {@link UrlAllowlist} before the socket opens. A
     * configured host that passes the allow list but resolves to a
     * private/reserved address is refused.
     */
    private void enforceAllowlist(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        try {
            UrlAllowlist.validate(baseUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Dialog outbound request blocked by SSRF allow list: {}", baseUrl);
            throw new DialogApiException(null, DialogApiException.DIALOG_SEC_001,
                    "Outbound URL blocked by SSRF allow list: " + baseUrl, e);
        }
    }

    // ================================================================
    // Internal Types
    // ================================================================

    /**
     * Per-tenant Dialog integration config resolved at request time.
     *
     * <p>Operator-specific API shapes are never hardcoded in the provider
     * classes. Everything that can differ between operators (base URL, LOB,
     * path templates, response field names, currency) is read from the
     * tenant's {@link TenantConfigurationService} configuration — edited in
     * the Admin Portal under Integrations:</p>
     *
     * <ul>
     *   <li>{@code metadata} — free-form operator values ({@code lob},
     *       {@code path.<resource>} templates, {@code field.<canonical>} names,
     *       {@code capabilities})</li>
     *   <li>{@code fieldMapping} — canonical → operator field-path mapping</li>
     * </ul>
     */
    public record DialogConfig(
            String tenantId,
            String baseUrl,
            String clientId,
            String clientSecret,
            String apiKey,
            String senderId,
            Map<String, String> metadata,
            Map<String, String> fieldMapping) {

        /** Look up an operator metadata value with a fallback default. */
        public String meta(String key, String fallback) {
            String v = metadata != null ? metadata.get(key) : null;
            return v != null && !v.isBlank() ? v : fallback;
        }

        /** Look up a canonical → operator field name with a fallback default. */
        public String mapping(String canonical, String fallback) {
            String v = fieldMapping != null ? fieldMapping.get(canonical) : null;
            if (v != null && !v.isBlank()) return v;
            return meta("field." + canonical, fallback);
        }

        public boolean isValid() {
            return baseUrl != null && !baseUrl.isEmpty();
        }

        public static DialogConfig empty(String tenantId) {
            return new DialogConfig(tenantId, null, null, null, null, null, null, null);
        }
    }

    private record TokenEntry(String token, Instant expiresAt,
                              String baseUrl, String clientId, String clientSecret) {}
}
