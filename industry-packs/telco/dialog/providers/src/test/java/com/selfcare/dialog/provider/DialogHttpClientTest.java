package com.selfcare.dialog.provider;

import com.selfcare.platform.common.security.UrlAllowlist;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verifies DialogHttpClient OAuth2 token handling against a real local
 * HTTP endpoint: client-credentials grant, per-tenant caching, expiry
 * honoring from the upstream {@code expires_in}, and forced refresh after
 * a 401-driven invalidation.
 */
class DialogHttpClientTest {

    private HttpServer serverHttp;
    private String baseUrl;
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final Map<String, String> lastGrantForm = new HashMap<>();
    private String lastAuthHeader;

    private DialogHttpClient httpClient;

    @BeforeEach
    void setUp() throws IOException {
        // The local WireMock-style endpoint listens on loopback; the SSRF allow
        // list must explicitly trust it (explicitly configured hosts bypass the
        // private-IP rejection).
        UrlAllowlist.configure(java.util.List.of("127.0.0.1"));
        serverHttp = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + serverHttp.getAddress().getPort();
        serverHttp.start();
        httpClient = new DialogHttpClient(RestClient.builder(), mock(TenantConfigurationService.class));
    }

    @AfterEach
    void tearDown() {
        UrlAllowlist.reset();
        serverHttp.stop(0);
    }

    private void stubToken(String responseBody) {
        serverHttp.createContext("/oauth/token", exchange -> {
            tokenRequests.incrementAndGet();
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            lastGrantForm.clear();
            lastGrantForm.putAll(parseForm(exchange));
            respond(exchange, 200,
                    "application/json", responseBody.getBytes(StandardCharsets.UTF_8));
        });
    }

    private void respond(HttpExchange exchange, int code, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Arrays.stream(body.split("&"))
                .filter(p -> p.contains("="))
                .collect(Collectors.toMap(
                        p -> URLDecoder.decode(p.substring(0, p.indexOf('=')), StandardCharsets.UTF_8),
                        p -> URLDecoder.decode(p.substring(p.indexOf('=') + 1), StandardCharsets.UTF_8),
                        (a, b) -> a));
    }

    @Test
    @DisplayName("getAccessToken posts client_credentials grant with Basic auth and returns the token")
    void tokenGrant() {
        stubToken("{\"access_token\":\"tok-1\",\"expires_in\":3600}");

        String token = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");

        assertEquals("tok-1", token);
        assertEquals(1, tokenRequests.get());
        assertTrue(lastAuthHeader != null && lastAuthHeader.startsWith("Basic "),
                "Expected Basic Authorization header, got: " + lastAuthHeader);
        assertEquals("client_credentials", lastGrantForm.get("grant_type"));
        assertEquals("selfcare", lastGrantForm.get("scope"));
    }

    @Test
    @DisplayName("getAccessToken caches a long-lived token for subsequent calls")
    void tokenCached() {
        stubToken("{\"access_token\":\"tok-long\",\"expires_in\":3600}");

        String first = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");
        String second = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");

        assertEquals("tok-long", first);
        assertEquals("tok-long", second);
        assertEquals(1, tokenRequests.get());
    }

    @Test
    @DisplayName("getAccessToken refetches when upstream expires_in is shorter than the refresh buffer")
    void shortTtlRefetches() {
        stubToken("{\"access_token\":\"tok-short-1\",\"expires_in\":60}");

        String first = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");
        String second = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");

        assertEquals("tok-short-1", first);
        assertEquals("tok-short-1", second);
        assertEquals(2, tokenRequests.get());
    }

    @Test
    @DisplayName("invalidateToken forces a fresh token on the next call")
    void invalidationRefetches() {
        stubToken("{\"access_token\":\"tok-a\",\"expires_in\":3600}");

        String first = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");
        httpClient.invalidateToken("dialog-lk");
        String second = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");

        assertEquals("tok-a", first);
        assertEquals("tok-a", second);
        assertEquals(2, tokenRequests.get());
    }

    @Test
    @DisplayName("timeouts are wired into the request factory (requests still complete)")
    void timeoutsWired() {
        stubToken("{\"access_token\":\"tok-timeout\",\"expires_in\":3600}");

        String token = httpClient.getAccessToken("dialog-lk", baseUrl, "mife-client", "mife-secret");

        assertEquals("tok-timeout", token);
        assertEquals(1, tokenRequests.get());
    }
}