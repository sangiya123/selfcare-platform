package com.omobio.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtRateLimitExclusionFilter.
 *
 * Verifies:
 * - /api/v1/auth/** paths get the skip attribute set
 * - Non-auth paths do NOT get the skip attribute
 * - Order is HIGHEST_PRECEDENCE + 200
 */
class JwtRateLimitExclusionFilterTest {

    private JwtRateLimitExclusionFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtRateLimitExclusionFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ======================================================================
    // Auth paths — rate limit skip set
    // ======================================================================

    @Test
    @DisplayName("/api/v1/auth/login sets rate-limit.skip=true")
    void authLogin_setsSkipAttribute() {
        ServerHttpRequest request = MockServerHttpRequest
            .post("/api/v1/auth/login")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(JwtRateLimitExclusionFilter.RATE_LIMIT_SKIP_ATTR))
            .isEqualTo(true);
    }

    @Test
    @DisplayName("/api/v1/auth/otp sets rate-limit.skip=true")
    void authOtp_setsSkipAttribute() {
        ServerHttpRequest request = MockServerHttpRequest
            .post("/api/v1/auth/otp")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(JwtRateLimitExclusionFilter.RATE_LIMIT_SKIP_ATTR))
            .isEqualTo(true);
    }

    @Test
    @DisplayName("/api/v1/auth/refresh sets rate-limit.skip=true")
    void authRefresh_setsSkipAttribute() {
        ServerHttpRequest request = MockServerHttpRequest
            .post("/api/v1/auth/refresh")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(JwtRateLimitExclusionFilter.RATE_LIMIT_SKIP_ATTR))
            .isEqualTo(true);
    }

    // ======================================================================
    // Non-auth paths — rate limit skip NOT set
    // ======================================================================

    @Test
    @DisplayName("/api/v1/customers/me does NOT set skip attribute")
    void customerPath_noSkipAttribute() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(JwtRateLimitExclusionFilter.RATE_LIMIT_SKIP_ATTR))
            .isNull();
    }

    @Test
    @DisplayName("/api/v1/admin/config does NOT set skip attribute")
    void adminPath_noSkipAttribute() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/admin/config")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(JwtRateLimitExclusionFilter.RATE_LIMIT_SKIP_ATTR))
            .isNull();
    }

    @Test
    @DisplayName("/actuator/health does NOT set skip attribute")
    void actuator_noSkipAttribute() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(JwtRateLimitExclusionFilter.RATE_LIMIT_SKIP_ATTR))
            .isNull();
    }

    // ======================================================================
    // Filter always passes to chain
    // ======================================================================

    @Test
    @DisplayName("Filter always calls chain.filter")
    void alwaysCallsChain() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    // ======================================================================
    // Filter order
    // ======================================================================

    @Test
    @DisplayName("Filter order is HIGHEST_PRECEDENCE + 200")
    void correctOrder() {
        assertThat(filter.getOrder()).isEqualTo(
            org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 200);
    }
}
