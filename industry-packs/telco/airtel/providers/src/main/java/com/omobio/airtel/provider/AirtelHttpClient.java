package com.omobio.airtel.provider;

import com.omobio.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared HTTP client for Airtel provider beans.
 *
 * <p>Airtel uses a combination of authentication styles:
 * <ul>
 *   <li>OAuth2 client_credentials with a token endpoint — used for the
 *       Airtel Money gateway to obtain short-lived bearer tokens.</li>
 *   <li>API-key in {@code X-API-Key} header — used for BSS/session calls.</li>
 * </ul>
 *
 * <p>This client supports both, with per-tenant OAuth2 token caching and
 * 5-minute proactive refresh. Also provides retries with exponential backoff
 * and structured {@link AirtelApiException} mapping.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirtelHttpClient {

    private static final Duration TOKEN_REFRESH_BUFFER = Duration.ofMinutes(5);
    private static final int MAX_RETRIES = 2;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient.Builder restClientBuilder;
    private final TenantConfigurationService tenantConfig;

    // Per-tenant OAuth2 token cache: tenantId -> { token, expiresAt }
    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    // ================================================================
    // Config Resolution
    // ================================================================

    /**
     * Resolve the active Airtel integration config for a tenant.
     */
    public AirtelConfig resolveConfig(String tenantId, String integrationType) {
        return tenantConfig.getIntegration(tenantId, integrationType)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> new AirtelConfig(
                        tenantId,
                        c.getBaseUrl(),
                        c.getCredential("clientId"),
                        c.getCredential("clientSecret"),
                        c.getCredential("apiKey"),
                        c.getCredential("merchantId"),
                        c.getCredential("senderId"),
                        c.getMetadata()))
                .orElseGet(() -> {
                    log.warn("No active {} integration for tenant={}", integrationType, tenantId);
                    return AirtelConfig.empty(tenantId);
                });
    }

    // ================================================================
    // Token Management
    // ================================================================

    /**
     * Get (or refresh) a valid OAuth2 bearer token for the tenant's Airtel Money gateway.
     */
    public String getAccessToken(String tenantId, String baseUrl, String clientId, String clientSecret) {
        Objects.requireNonNull(tenantId, "tenantId");
        TokenEntry cached = tokenCache.get(tenantId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plus(TOKEN_REFRESH_BUFFER))) {
            return cached.token();
        }
        String token = fetchNewToken(tenantId, baseUrl, clientId, clientSecret);
        tokenCache.put(tenantId, new TokenEntry(token, Instant.now().plus(Duration.ofMinutes(55))));
        return token;
    }

    /**
     * Discard the cached token for a tenant. Called after receiving 401.
     */
    public void invalidateToken(String tenantId) {
        tokenCache.remove(tenantId);
        log.debug("Airtel OAuth2 token invalidated for tenant={}", tenantId);
    }

    @SuppressWarnings("unchecked")
    private String fetchNewToken(String tenantId, String baseUrl, String clientId, String clientSecret) {
        String tokenUrl = baseUrl + "/v1/osp/auth/token";
        log.debug("Fetching Airtel OAuth2 token for tenant={}", tenantId);

        RestClient client = buildClient(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (clientId != null && clientSecret != null) {
            headers.setBasicAuth(clientId, clientSecret);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "airtel-money");

        try {
            ResponseEntity<Map> resp = client.post()
                    .uri(tokenUrl)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);

            if (resp.getBody() == null || resp.getBody().get("access_token") == null) {
                throw new AirtelApiException(resp.getStatusCode(), AirtelApiException.AIRTEL_AUTH_003,
                        "Empty token response from Airtel Money gateway");
            }
            String token = (String) resp.getBody().get("access_token");
            log.info("Airtel OAuth2 token obtained for tenant={}, expires_in={}",
                    tenantId, resp.getBody().getOrDefault("expires_in", "unknown"));
            return token;
        } catch (HttpClientErrorException e) {
            log.error("Airtel token fetch failed for tenant={}: HTTP {} — {}",
                    tenantId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AirtelApiException(e.getStatusCode(), AirtelApiException.AIRTEL_AUTH_003,
                    "Failed to obtain Airtel token: " + e.getMessage(), e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Airtel OAuth2 endpoint error for tenant={}: {}", tenantId, e.getMessage());
            throw new AirtelApiException(null, AirtelApiException.AIRTEL_GEN_503,
                    "Airtel Money gateway unavailable", e);
        }
    }

    // ================================================================
    // HTTP Methods
    // ================================================================

    /**
     * GET — returns parsed body as {@code Map}, or {@code null} on 404.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String tenantId, AirtelConfig cfg, String path,
                                   Map<String, ? extends Object> queryParams,
                                   String bearerToken) {
        RestClient client = buildClient(cfg.baseUrl());
        String uri = buildUri(cfg.baseUrl() + path, queryParams);
        HttpHeaders headers = authHeaders(bearerToken, cfg.apiKey());

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
                    // Retry with refreshed token
                    String refreshed = getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
                    headers.setBearerAuth(refreshed);
                    continue;
                }
                throw toAirtelException(e, path);
            } catch (HttpClientErrorException e) {
                throw toAirtelException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AirtelApiException(e.getStatusCode(), AirtelApiException.AIRTEL_GEN_500,
                        "Airtel upstream error on GET " + path, e);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AirtelApiException(null, AirtelApiException.AIRTEL_GEN_503,
                        "Airtel upstream timeout on GET " + path, e);
            }
        }
        return null;
    }

    /**
     * POST with JSON body — returns parsed body as {@code Map}.
     */
    public Map<String, Object> post(String tenantId, AirtelConfig cfg, String path,
                                   Object body, String bearerToken) {
        return execute(HttpMethod.POST, tenantId, cfg, path, null, body, bearerToken);
    }

    /**
     * PUT with JSON body.
     */
    public Map<String, Object> put(String tenantId, AirtelConfig cfg, String path,
                                   Object body, String bearerToken) {
        return execute(HttpMethod.PUT, tenantId, cfg, path, null, body, bearerToken);
    }

    /**
     * DELETE — no body returned.
     */
    public void delete(String tenantId, AirtelConfig cfg, String path, String bearerToken) {
        RestClient client = buildClient(cfg.baseUrl());
        HttpHeaders headers = authHeaders(bearerToken, cfg.apiKey());
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                client.delete()
                        .uri(cfg.baseUrl() + path)
                        .headers(h -> h.addAll(headers))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException.Unauthorized e) {
                if (attempt == 0 && bearerToken != null) {
                    invalidateToken(tenantId);
                    String refreshed = getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
                    headers.setBearerAuth(refreshed);
                    continue;
                }
                throw toAirtelException(e, path);
            } catch (HttpClientErrorException e) {
                throw toAirtelException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AirtelApiException(e.getStatusCode(), AirtelApiException.AIRTEL_GEN_500,
                        "Airtel upstream error on DELETE " + path, e);
            }
        }
    }

    // ================================================================
    // Internal Helpers
    // ================================================================

    private Map<String, Object> execute(HttpMethod method, String tenantId, AirtelConfig cfg, String path,
                                         Map<String, ? extends Object> queryParams,
                                         Object body, String bearerToken) {
        RestClient client = buildClient(cfg.baseUrl());
        String uri = buildUri(cfg.baseUrl() + path, queryParams);
        HttpHeaders headers = authHeaders(bearerToken, cfg.apiKey());
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
                    String refreshed = getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
                    headers.setBearerAuth(refreshed);
                    continue;
                }
                throw toAirtelException(e, path);
            } catch (HttpClientErrorException e) {
                throw toAirtelException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AirtelApiException(e.getStatusCode(), AirtelApiException.AIRTEL_GEN_500,
                        "Airtel upstream error on " + method + " " + path, e);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AirtelApiException(null, AirtelApiException.AIRTEL_GEN_503,
                        "Airtel upstream timeout on " + method + " " + path, e);
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
        if (body != null) {
            // Airtel Money uses { "data": {...}, "success": true } shape
            if (body.get("error") instanceof Map<?, ?> err) {
                throw new AirtelApiException(resp.getStatusCode(),
                        (String) ((Map<String, Object>) err).getOrDefault("code", AirtelApiException.AIRTEL_GEN_500),
                        (String) ((Map<String, Object>) err).getOrDefault("message", "Unknown error"),
                        (Map<String, Object>) err);
            }
            if (body.get("code") instanceof String code) {
                throw new AirtelApiException(resp.getStatusCode(), code,
                        (String) body.getOrDefault("message", "Unknown error"), body);
            }
        }
        throw new AirtelApiException(resp.getStatusCode(), AirtelApiException.AIRTEL_GEN_500,
                "Unexpected Airtel response: " + resp.getStatusCode());
    }

    private HttpHeaders authHeaders(String bearerToken, String apiKey) {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (bearerToken != null) h.setBearerAuth(bearerToken);
        if (apiKey != null) h.set("X-API-Key", apiKey);
        return h;
    }

    private String buildUri(String path, Map<String, ? extends Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) return path;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
        queryParams.forEach((k, v) -> {
            if (v != null) builder.queryParam(k, v);
        });
        return builder.build().toUriString();
    }

    private AirtelApiException toAirtelException(HttpClientErrorException e, String path) {
        String code = AirtelApiException.AIRTEL_GEN_500;
        Map<String, Object> payload = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = e.getResponseBodyAs(Map.class);
            if (body != null) {
                payload = body;
                if (body.get("error") instanceof Map<?, ?> err) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> em = (Map<String, Object>) err;
                    code = (String) em.getOrDefault("code", code);
                } else if (body.get("code") instanceof String s) {
                    code = s;
                }
            }
        } catch (Exception ignored) {}
        return new AirtelApiException(e.getStatusCode(), code,
                String.format("Airtel API error on %s: %s", path, e.getMessage()), payload);
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

    private RestClient buildClient(String baseUrl) {
        return restClientBuilder.baseUrl(Objects.requireNonNullElse(baseUrl, "")).build();
    }

    // ================================================================
    // Internal Types
    // ================================================================

    /**
     * Per-tenant Airtel integration config resolved at request time.
     */
    public record AirtelConfig(
            String tenantId,
            String baseUrl,
            String clientId,
            String clientSecret,
            String apiKey,
            String merchantId,
            String senderId,
            Map<String, String> metadata) {
        public boolean isValid() { return baseUrl != null && !baseUrl.isEmpty(); }
        public static AirtelConfig empty(String tenantId) {
            return new AirtelConfig(tenantId, null, null, null, null, null, null, null);
        }
    }

    private record TokenEntry(String token, Instant expiresAt) {}
}
