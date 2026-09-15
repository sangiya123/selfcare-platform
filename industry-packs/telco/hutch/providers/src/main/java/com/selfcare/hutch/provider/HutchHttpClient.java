package com.selfcare.hutch.provider;

import com.selfcare.platform.common.security.UrlAllowlist;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Shared HTTP client for Hutch provider beans.
 *
 * <p>Hutch uses API-key authentication (no OAuth2). This client provides:
 * <ul>
 *   <li>Per-tenant config resolution via {@link TenantConfigurationService}</li>
 *   <li>Retry with exponential backoff on transient failures (502, 503, 504)</li>
 *   <li>Structured {@link HutchApiException} mapping from Hutch error responses</li>
 *   <li>Bearer-token support for auth endpoints that use session tokens</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HutchHttpClient {

    private static final int MAX_RETRIES = 2;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient.Builder restClientBuilder;
    private final TenantConfigurationService tenantConfig;

    // ================================================================
    // Config Resolution
    // ================================================================

    /**
     * Resolve the active Hutch BSS integration config for a tenant.
     */
    public HutchConfig resolveConfig(String tenantId, String integrationType) {
        return tenantConfig.getIntegration(tenantId, integrationType)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> new HutchConfig(
                        tenantId,
                        c.getBaseUrl(),
                        c.getCredential("apiKey"),
                        c.getCredential("clientId"),
                        c.getCredential("clientSecret"),
                        c.getCredential("senderId"),
                        c.getMetadata()))
                .orElseGet(() -> {
                    log.warn("No active {} integration for tenant={}", integrationType, tenantId);
                    return HutchConfig.empty(tenantId);
                });
    }

    // ================================================================
    // HTTP Methods
    // ================================================================

    /**
     * GET — returns parsed body as {@code Map}, or {@code null} on 404.
     */
    public Map<String, Object> get(String tenantId, HutchConfig cfg, String path,
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
            } catch (HttpClientErrorException e) {
                throw toHutchException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new HutchApiException(e.getStatusCode(), HutchApiException.HUTCH_GEN_500,
                        "Hutch upstream error on GET " + path, e);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new HutchApiException(null, HutchApiException.HUTCH_GEN_503,
                        "Hutch upstream timeout on GET " + path, e);
            }
        }
        return null;
    }

    /**
     * POST with JSON body — returns parsed body as {@code Map}.
     */
    public Map<String, Object> post(String tenantId, HutchConfig cfg, String path,
                                    Object body, String bearerToken) {
        RestClient client = buildClient(cfg.baseUrl());
        String uri = buildUri(cfg.baseUrl() + path, null);
        HttpHeaders headers = authHeaders(bearerToken, cfg.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<Map> resp = client.post()
                        .uri(uri)
                        .headers(h -> h.addAll(headers))
                        .body(body)
                        .retrieve()
                        .toEntity(Map.class);
                return handleResponse(resp);
            } catch (HttpClientErrorException e) {
                throw toHutchException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new HutchApiException(e.getStatusCode(), HutchApiException.HUTCH_GEN_500,
                        "Hutch upstream error on POST " + path, e);
            } catch (ResourceAccessException e) {
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new HutchApiException(null, HutchApiException.HUTCH_GEN_503,
                        "Hutch upstream timeout on POST " + path, e);
            }
        }
        return null;
    }

    /**
     * PUT with JSON body.
     */
    public Map<String, Object> put(String tenantId, HutchConfig cfg, String path,
                                   Object body, String bearerToken) {
        return execute(HttpMethod.PUT, tenantId, cfg, path, null, body, bearerToken);
    }

    /**
     * DELETE — no body returned.
     */
    public void delete(String tenantId, HutchConfig cfg, String path, String bearerToken) {
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
            } catch (HttpClientErrorException e) {
                throw toHutchException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new HutchApiException(e.getStatusCode(), HutchApiException.HUTCH_GEN_500,
                        "Hutch upstream error on DELETE " + path, e);
            }
        }
    }

    // ================================================================
    // Internal Helpers
    // ================================================================

    private Map<String, Object> execute(HttpMethod method, String tenantId, HutchConfig cfg, String path,
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
            } catch (HttpClientErrorException e) {
                throw toHutchException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new HutchApiException(e.getStatusCode(), HutchApiException.HUTCH_GEN_500,
                        "Hutch upstream error on " + method + " " + path, e);
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
            // Hutch wraps errors under "error" or "message" key
            if (body.get("error") instanceof Map<?, ?> err) {
                throw new HutchApiException(resp.getStatusCode(),
                        (String) ((Map<String, Object>) err).getOrDefault("code", HutchApiException.HUTCH_GEN_500),
                        (String) ((Map<String, Object>) err).getOrDefault("message", "Unknown error"),
                        (Map<String, Object>) err);
            }
            if (body.get("code") instanceof String code) {
                throw new HutchApiException(resp.getStatusCode(), code,
                        (String) body.getOrDefault("message", "Unknown error"), body);
            }
        }
        throw new HutchApiException(resp.getStatusCode(), HutchApiException.HUTCH_GEN_500,
                "Unexpected Hutch response: " + resp.getStatusCode());
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

    private HutchApiException toHutchException(HttpClientErrorException e, String path) {
        String code = HutchApiException.HUTCH_GEN_500;
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
        return new HutchApiException(e.getStatusCode(), code,
                String.format("Hutch API error on %s: %s", path, e.getMessage()), payload);
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
        enforceAllowlist(baseUrl);
        return restClientBuilder.baseUrl(Objects.requireNonNullElse(baseUrl, "")).build();
    }

    /**
     * Live SSRF enforcement: every outbound request to a Hutch endpoint is
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
            log.warn("Hutch outbound request blocked by SSRF allow list: {}", baseUrl);
            throw new HutchApiException(null, HutchApiException.HUTCH_SEC_001,
                    "Outbound URL blocked by SSRF allow list: " + baseUrl, e);
        }
    }

    // ================================================================
    // Internal Types
    // ================================================================

    /**
     * Per-tenant Hutch integration config resolved at request time.
     */
    public record HutchConfig(
            String tenantId,
            String baseUrl,
            String apiKey,
            String clientId,
            String clientSecret,
            String senderId,
            Map<String, String> metadata) {
        public boolean isValid() { return baseUrl != null && !baseUrl.isEmpty(); }
        public static HutchConfig empty(String tenantId) {
            return new HutchConfig(tenantId, null, null, null, null, null, null);
        }
    }
}
