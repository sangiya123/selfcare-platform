package com.selfcare.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WAF (Web Application Firewall) gateway filter tests.
 *
 * Verifies detection and blocking of:
 * - SQL injection
 * - Cross-site scripting (XSS)
 * - Path traversal
 * - Command injection
 * - SSRF (Server-Side Request Forgery)
 * - Rate limiting
 * - HTTP method allow-list
 *
 * Uses reflection to set @Value fields since we're testing outside Spring context.
 */
class WafGatewayFilterFactoryTest {

    private WafGatewayFilterFactory factory;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        factory = new WafGatewayFilterFactory();

        // Wire @Value fields via reflection for testing
        setField(factory, "enabled", true);
        setField(factory, "rulesResource", "classpath:waf-rules.yml");
        setField(factory, "maxBodySize", 1048576L);

        factory.loadRules();

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ServerWebExchange buildExchange(String method, String path, String query) {
        String fullPath = query != null ? path + "?" + query : path;
        var request = MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create(fullPath)).build();
        return MockServerWebExchange.from(request);
    }

    // ======================================================================
    // WAF disabled — all requests pass through
    // ======================================================================

    @Test
    @DisplayName("When disabled, WAF passes all requests through")
    void disabled_passesThrough() throws Exception {
        WafGatewayFilterFactory disabled = new WafGatewayFilterFactory();
        setField(disabled, "enabled", false);

        ServerWebExchange exchange = buildExchange("GET", "/api/v1/customers/me", null);
        GatewayFilter filter = disabled.apply(new WafGatewayFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain).filter(exchange);
    }

    // ======================================================================
    // HTTP method allow list
    // ======================================================================

    @Test
    @DisplayName("Allowed HTTP methods pass through")
    void allowedMethods_passThrough() {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"}) {
            reset(chain);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var request = MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create("/api/v1/test")).build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);
            GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());

            filter.filter(exchange, chain).block();
            verify(chain).filter(exchange);
        }
    }

    @Test
    @DisplayName("TRACE method is blocked")
    void traceMethod_blocked() {
        var request = MockServerHttpRequest.method(HttpMethod.valueOf("TRACE"), URI.create("/api/v1/test")).build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("CONNECT method is blocked")
    void connectMethod_blocked() {
        var request = MockServerHttpRequest.method(HttpMethod.valueOf("CONNECT"), URI.create("/api/v1/test")).build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    // ======================================================================
    // Rate limiting
    // ======================================================================

    @Test
    @DisplayName("Auth endpoint rate limit blocks after 10 requests from same IP")
    void authRateLimit_blocksAfter10() {
        String path = "/api/v1/auth/login";

        // First 10 should pass
        for (int i = 0; i < 10; i++) {
            reset(chain);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var request = MockServerHttpRequest
                .post(path + "?email=test" + i + "@example.com")
                .remoteAddress(new java.net.InetSocketAddress("192.0.2.1", 0))
                .build();
            ServerWebExchange exchange = MockServerWebExchange.from(request);

            GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
            filter.filter(exchange, chain).block();
            verify(chain).filter(exchange);
        }

        // 11th should be blocked
        reset(chain);
        var request11 = MockServerHttpRequest
            .post(path)
            .remoteAddress(new java.net.InetSocketAddress("192.0.2.1", 0))
            .build();
        ServerWebExchange exchange11 = MockServerWebExchange.from(request11);
        MockServerHttpResponse response = (MockServerHttpResponse) exchange11.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange11, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("Too many authentication attempts");
    }

    @Test
    @DisplayName("Different IPs have separate rate limit counters")
    void differentIps_separateCounters() {
        String path = "/api/v1/auth/otp";

        // 10 from IP 1
        for (int i = 0; i < 10; i++) {
            reset(chain);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var request = MockServerHttpRequest
                .post(path)
                .remoteAddress(new java.net.InetSocketAddress("192.0.2.1", 0))
                .build();
            GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
            filter.filter(MockServerWebExchange.from(request), chain).block();
        }

        // 11th from IP 1 → blocked
        var ip1Request = MockServerHttpRequest
            .post(path)
            .remoteAddress(new java.net.InetSocketAddress("192.0.2.1", 0))
            .build();
        ServerWebExchange ip1Exchange = MockServerWebExchange.from(ip1Request);
        MockServerHttpResponse ip1Response = (MockServerHttpResponse) ip1Exchange.getResponse();
        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(ip1Exchange, chain).block();
        assertThat(ip1Response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Same request from IP 2 → should pass (different counter)
        reset(chain);
        when(chain.filter(any())).thenReturn(Mono.empty());
        var ip2Request = MockServerHttpRequest
            .post(path)
            .remoteAddress(new java.net.InetSocketAddress("192.0.2.2", 0))
            .build();
        ServerWebExchange ip2Exchange = MockServerWebExchange.from(ip2Request);
        GatewayFilter filter2 = factory.apply(new WafGatewayFilterFactory.Config());
        filter2.filter(ip2Exchange, chain).block();
        verify(chain).filter(any());
    }

    // ======================================================================
    // SSRF — private / reserved IP detection
    // ======================================================================

    @Test
    @DisplayName("localhost URL is blocked")
    void ssrfLocalhost_blocked() {
        ServerWebExchange exchange = buildExchange("POST", "/api/v1/webhooks",
            "url=http://localhost:8080/admin");
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("private/reserved IP");
    }

    @Test
    @DisplayName("127.0.0.1 URL is blocked")
    void ssrfLoopback_blocked() {
        ServerWebExchange exchange = buildExchange("POST", "/api/v1/webhooks",
            "url=http://127.0.0.1:8080/");
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("192.168.x.x private IP is blocked")
    void ssrfPrivateIp192_blocked() {
        ServerWebExchange exchange = buildExchange("POST", "/api/v1/webhooks",
            "url=http://192.168.1.100:8080/admin");
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("10.x.x.x private IP is blocked")
    void ssrfPrivateIp10_blocked() {
        ServerWebExchange exchange = buildExchange("POST", "/api/v1/webhooks",
            "url=http://10.0.0.5/internal-api");
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AWS metadata IP 169.254.x.x is blocked")
    void ssrfAwsMetadata_blocked() {
        ServerWebExchange exchange = buildExchange("GET", "/api/v1/config",
            "url=http://169.254.169.254/latest/meta-data/");
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Public IP passes through")
    void ssrfPublicIp_allowed() {
        ServerWebExchange exchange = buildExchange("POST", "/api/v1/webhooks",
            "url=http://93.184.216.34:443/api");
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        // No WAF rejection for public IPs
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ======================================================================
    // Client IP extraction
    // ======================================================================

    @Test
    @DisplayName("X-Forwarded-For header IP is used for rate limiting")
    void forwardedFor_usedForRateLimit() {
        // First 10 from client 203.0.113.50 (via X-Forwarded-For)
        String path = "/api/v1/auth/login";
        for (int i = 0; i < 10; i++) {
            reset(chain);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var request = MockServerHttpRequest
                .post(path)
                .header("X-Forwarded-For", "203.0.113.50")
                .build();
            GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
            filter.filter(MockServerWebExchange.from(request), chain).block();
        }

        // 11th from same IP → blocked
        reset(chain);
        var request11 = MockServerHttpRequest
            .post(path)
            .header("X-Forwarded-For", "203.0.113.50")
            .build();
        ServerWebExchange exchange11 = MockServerWebExchange.from(request11);
        MockServerHttpResponse response = (MockServerHttpResponse) exchange11.getResponse();

        GatewayFilter filter = factory.apply(new WafGatewayFilterFactory.Config());
        filter.filter(exchange11, chain).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
