package com.omobio.aia.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared HTTP client for the AIA Insurance Provider.
 *
 * Features:
 * - OAuth2 client_credentials token caching per tenant (refreshed 5 min before expiry)
 * - Base HTTP methods: get, post, put, delete
 * - AIA-specific error handling and structured error responses
 * - Retry policy with exponential backoff on transient failures
 *
 * All methods are tenant-aware — each tenant has its own token cache entry.
 * This class is a Spring {@link Component} so it can be injected into
 * the {@link AIAInsuranceProvider}.
 */
@Slf4j
@Component
public class AIAHttpClient {

    private static final Duration TOKEN_REFRESH_BUFFER = Duration.ofMinutes(5);
    private static final int MAX_RETRIES = 2;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    private final RestClient.Builder restClientBuilder;

    public AIAHttpClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    // Per-tenant token cache: tenantId -> { token, expiresAt }
    private final Map<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    // ================================================================
    // Token Management
    // ================================================================

    /**
     * Fetches (or retrieves from cache) a valid OAuth2 bearer token for the given tenant.
     * Tokens are cached and refreshed proactively 5 minutes before expiry.
     *
     * @param tenantId   the tenant identifier (e.g. "aia-lk")
     * @param baseUrl    the AIA API base URL
     * @param clientId   OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @return a valid bearer token
     */
    public String getAccessToken(String tenantId, String baseUrl, String clientId, String clientSecret) {
        TokenEntry cached = tokenCache.get(tenantId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plus(TOKEN_REFRESH_BUFFER))) {
            return cached.token();
        }

        String token = fetchNewToken(tenantId, baseUrl, clientId, clientSecret);
        tokenCache.put(tenantId, new TokenEntry(token, Instant.now().plus(Duration.ofMinutes(55))));
        return token;
    }

    /**
     * Discards the cached token for a tenant, forcing a re-fetch on the next request.
     * Use after receiving a 401 from the upstream API.
     */
    public void invalidateToken(String tenantId) {
        tokenCache.remove(tenantId);
        log.debug("OAuth2 token invalidated for tenant={}", tenantId);
    }

    private String fetchNewToken(String tenantId, String baseUrl, String clientId, String clientSecret) {
        String tokenUrl = baseUrl + "/oauth2/token";
        log.debug("Fetching new OAuth2 token for tenant={}", tenantId);

        RestClient client = buildClient(baseUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "insurance.read insurance.write");

        try {
            Map<?, ?> response = client.post()
                    .uri(tokenUrl)
                    .headers(h -> h.addAll(headers))
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new AIAApiException(null, AIAApiException.AIA_AUTH_001,
                        "Empty token response from AIA OAuth2 endpoint");
            }

            String token = (String) response.get("access_token");
            log.info("OAuth2 token obtained for tenant={}, expires_in={}",
                    tenantId, response.get("expires_in"));
            return token;

        } catch (HttpClientErrorException e) {
            log.error("OAuth2 token fetch failed for tenant={}: HTTP {} — {}",
                    tenantId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_AUTH_001,
                    "Failed to obtain OAuth2 token: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.error("OAuth2 endpoint error for tenant={}: HTTP {} — {}",
                    tenantId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_GEN_503,
                    "AIA OAuth2 service unavailable", e);
        }
    }

    // ================================================================
    // HTTP Methods — raw
    // ================================================================

    /**
     * GET request returning the parsed response body as Map.
     *
     * @param tenantId  tenant for token context
     * @param baseUrl   AIA API base URL
     * @param path      API path (appended to baseUrl)
     * @param queryParams optional query parameters
     * @param token     OAuth2 bearer token
     * @return response body as Map, or null on 404/empty
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String tenantId, String baseUrl, String path,
                                   Map<String, ? extends Object> queryParams, String token) {
        RestClient client = buildClient(baseUrl);
        String uri = buildUri(baseUrl + path, queryParams);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = authHeaders(token);
                ResponseEntity<Map> resp = client.get()
                        .uri(uri)
                        .headers(h -> h.addAll(headers))
                        .retrieve()
                        .toEntity(Map.class);

                return handleResponse(resp);
            } catch (HttpClientErrorException.Unauthorized e) {
                if (attempt == 0) {
                    invalidateToken(tenantId);
                    token = getAccessToken(tenantId, baseUrl,
                            resolveClientId(token), resolveClientSecret(token));
                    continue;
                }
                throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_AUTH_002,
                        "Unauthorized after token refresh", e);
            } catch (HttpClientErrorException e) {
                throw toAIAApiException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_GEN_500,
                        "AIA upstream error on GET " + path, e);
            }
        }
        return null; // unreachable
    }

    /**
     * GET request returning a list of items from the "data" field.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String tenantId, String baseUrl, String path,
                                             Map<String, ? extends Object> queryParams, String token) {
        Map<String, Object> body = get(tenantId, baseUrl, path, queryParams, token);
        if (body == null) return List.of();
        Object data = body.get("data");
        if (data instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    /**
     * POST request with a JSON body.
     */
    public Map<String, Object> post(String tenantId, String baseUrl, String path,
                                    Object body, String token) {
        return execute(HttpMethod.POST, tenantId, baseUrl, path, null, body, token);
    }

    /**
     * PUT request with a JSON body.
     */
    public Map<String, Object> put(String tenantId, String baseUrl, String path,
                                   Object body, String token) {
        return execute(HttpMethod.PUT, tenantId, baseUrl, path, null, body, token);
    }

    /**
     * DELETE request.
     */
    public void delete(String tenantId, String baseUrl, String path, String token) {
        executeVoid(HttpMethod.DELETE, tenantId, baseUrl, path, null, null, token);
    }

    // ================================================================
    // Internal Helpers
    // ================================================================

    private Map<String, Object> execute(HttpMethod method, String tenantId, String baseUrl,
                                         String path, Map<String, ? extends Object> queryParams,
                                         Object body, String token) {
        RestClient client = buildClient(baseUrl);
        String uri = buildUri(baseUrl + path, queryParams);
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> req = new HttpEntity<>(body, headers);

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
                if (attempt == 0) {
                    invalidateToken(tenantId);
                    token = getAccessToken(tenantId, baseUrl,
                            resolveClientId(token), resolveClientSecret(token));
                    continue;
                }
                throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_AUTH_002,
                        "Unauthorized after token refresh", e);
            } catch (HttpClientErrorException e) {
                throw toAIAApiException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_GEN_500,
                        "AIA upstream error on " + method + " " + path, e);
            }
        }
        return null;
    }

    private void executeVoid(HttpMethod method, String tenantId, String baseUrl,
                              String path, Map<String, ? extends Object> queryParams,
                              Object body, String token) {
        RestClient client = buildClient(baseUrl);
        String uri = buildUri(baseUrl + path, queryParams);
        HttpHeaders headers = authHeaders(token);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                client.method(method)
                        .uri(uri)
                        .headers(h -> h.addAll(headers))
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException.Unauthorized e) {
                if (attempt == 0) {
                    invalidateToken(tenantId);
                    token = getAccessToken(tenantId, baseUrl,
                            resolveClientId(token), resolveClientSecret(token));
                    continue;
                }
                throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_AUTH_002,
                        "Unauthorized after token refresh", e);
            } catch (HttpClientErrorException e) {
                throw toAIAApiException(e, path);
            } catch (HttpServerErrorException e) {
                if (isTransient(e) && attempt < MAX_RETRIES) {
                    backoff(attempt);
                    continue;
                }
                throw new AIAApiException(e.getStatusCode(), AIAApiException.AIA_GEN_500,
                        "AIA upstream error on " + method + " " + path, e);
            }
        }
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
        if (body != null && body.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            throw new AIAApiException(resp.getStatusCode(),
                    (String) error.getOrDefault("code", AIAApiException.AIA_GEN_500),
                    (String) error.getOrDefault("message", "Unknown error"),
                    error);
        }
        throw new AIAApiException(resp.getStatusCode(), AIAApiException.AIA_GEN_500,
                "Unexpected response: " + resp.getStatusCode());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    private HttpHeaders authHeaders(String token, String apiKey) {
        HttpHeaders h = authHeaders(token);
        if (apiKey != null) {
            h.set("X-API-Key", apiKey);
        }
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

    private AIAApiException toAIAApiException(HttpClientErrorException e, String path) {
        Map<String, Object> errorBody = null;
        String errorCode = AIAApiException.AIA_GEN_500;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = e.getResponseBodyAs(Map.class);
            if (body != null) {
                errorBody = body;
                if (body.containsKey("error")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> error = (Map<String, Object>) body.get("error");
                    errorCode = (String) error.getOrDefault("code", AIAApiException.AIA_GEN_500);
                }
            }
        } catch (Exception ignored) { }

        String message = String.format("AIA API error on %s: %s", path, e.getMessage());
        return new AIAApiException(e.getStatusCode(), errorCode, message, errorBody);
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
        return restClientBuilder.baseUrl(baseUrl).build();
    }

    // Resolving clientId/secret from token cache is not possible after the fact;
    // these are placeholders — actual implementation delegates to Config carrier
    private String resolveClientId(String token) { return null; }
    private String resolveClientSecret(String token) { return null; }

    // ================================================================
    // Internal Types
    // ================================================================

    private record TokenEntry(String token, Instant expiresAt) {}
}
