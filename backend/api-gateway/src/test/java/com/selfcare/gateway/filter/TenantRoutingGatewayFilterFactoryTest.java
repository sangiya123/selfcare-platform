package com.selfcare.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantRoutingGatewayFilterFactory.
 *
 * Verifies:
 * - X-Tenant-Id validation (missing, blank, invalid format)
 * - Correlation ID generation and propagation
 * - Client IP resolution from X-Forwarded-For / X-Real-IP
 * - Stripping of client-supplied identity headers
 * - Environment detection from Host header / SELFCARE_ENV
 */
class TenantRoutingGatewayFilterFactoryTest {

    private TenantRoutingGatewayFilterFactory factory;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        factory = new TenantRoutingGatewayFilterFactory();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private ServerWebExchange buildExchange(String tenantId) {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", tenantId)
            .header("Host", "dialog-lk.selfcare.io")
            .build();
        return MockServerWebExchange.from(request);
    }

    // ======================================================================
    // Valid requests — filter should forward with mutated headers
    // ======================================================================

    @Test
    @DisplayName("Valid tenant ID passes through and sets all required headers")
    void validTenantId_passesThrough() {
        ServerWebExchange exchange = buildExchange("dialog-lk");

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            ServerHttpRequest req = ex.getRequest();
            // Tenant ID must be set
            assertThat(req.getHeaders().getFirst("X-Tenant-Id")).isEqualTo("dialog-lk");
            // Correlation ID must be set
            String corrId = req.getHeaders().getFirst("X-Correlation-Id");
            assertThat(corrId).isNotNull().startsWith("selfcare-");
            // Environment from Host header
            assertThat(req.getHeaders().getFirst("X-Environment")).isEqualTo("dev");
            // Client IP
            assertThat(req.getHeaders().getFirst("X-Forwarded-For")).isNotNull();
            return true;
        }));
    }

    @Test
    @DisplayName("Correlation ID from request header is preserved")
    void correlationId_preserved() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "hutch-lk")
            .header("X-Correlation-Id", "existing-corr-123")
            .header("Host", "hutch-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex ->
            "existing-corr-123".equals(ex.getRequest().getHeaders().getFirst("X-Correlation-Id"))
        ));
    }

    @Test
    @DisplayName("Client IP from X-Forwarded-For is used (leftmost IP)")
    void clientIp_fromXForwardedFor() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "airtel-lk")
            .header("X-Forwarded-For", "203.0.113.50, 10.0.0.1, 192.168.1.1")
            .header("Host", "airtel-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex ->
            "203.0.113.50".equals(ex.getRequest().getHeaders().getFirst("X-Forwarded-For"))
        ));
    }

    @Test
    @DisplayName("Client IP from X-Real-IP is used as fallback")
    void clientIp_fromXRealIp() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "dialog-lk")
            .header("X-Real-IP", "198.51.100.25")
            .header("Host", "dialog-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex ->
            "198.51.100.25".equals(ex.getRequest().getHeaders().getFirst("X-Forwarded-For"))
        ));
    }

    @Test
    @DisplayName("Stripped headers are removed from inbound request")
    void strippedHeaders_removed() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "dialog-lk")
            .header("X-User-Id", "attacker-set-this")
            .header("X-User-Scopes", "admin")
            .header("X-Session-Id", "hijacked-session")
            .header("Host", "dialog-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            HttpHeaders headers = ex.getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isNull();
            assertThat(headers.getFirst("X-User-Scopes")).isNull();
            assertThat(headers.getFirst("X-Session-Id")).isNull();
            // But new ones are set downstream by JwtAuthenticationHeaderRelay
            return true;
        }));
    }

    // ======================================================================
    // Invalid requests — filter short-circuits with 400
    // ======================================================================

    @Test
    @DisplayName("Missing X-Tenant-Id header returns 400")
    void missingTenantId_returns400() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("Host", "dialog-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
            .verifyComplete();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Blank X-Tenant-Id header returns 400")
    void blankTenantId_returns400() {
        ServerWebExchange exchange = buildExchange("  ");

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Invalid tenant ID format returns 400")
    void invalidTenantIdFormat_returns400() {
        // Contains spaces and special chars — not matching ^[a-zA-Z0-9\\-]{1,64}$
        ServerWebExchange exchange = buildExchange("dialog lk!");

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Tenant ID exceeding 64 characters returns 400")
    void tenantIdTooLong_returns400() {
        String longTenantId = "a".repeat(65);
        ServerWebExchange exchange = buildExchange(longTenantId);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Valid tenant IDs with edge cases pass through")
    void edgeCaseTenantIds_passThrough() {
        // Single char
        ServerWebExchange exchange1 = buildExchange("a");
        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange1, chain).block();
        verify(chain, atLeastOnce()).filter(any());

        reset(chain);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // 64 chars
        String maxTenantId = "a".repeat(64);
        ServerWebExchange exchange2 = buildExchange(maxTenantId);
        filter.filter(exchange2, chain).block();
        verify(chain, atLeastOnce()).filter(any());
    }

    // ======================================================================
    // Environment detection
    // ======================================================================

    @Test
    @DisplayName("stg host suffix resolves to stg environment")
    void environment_stg() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "hutch-lk")
            .header("Host", "stg-api.hutch-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex ->
            "stg".equals(ex.getRequest().getHeaders().getFirst("X-Environment"))
        ));
    }

    @Test
    @DisplayName("prod host suffix resolves to prod environment")
    void environment_prod() {
        var request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "dialog-lk")
            .header("Host", "prod-api.dialog-lk.selfcare.io")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = factory.apply(new TenantRoutingGatewayFilterFactory.Config());
        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex ->
            "prod".equals(ex.getRequest().getHeaders().getFirst("X-Environment"))
        ));
    }
}
