package com.omobio.gateway.filter;

import com.omobio.platform.common.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationHeaderRelay.
 *
 * Verifies:
 * - Valid JWT: headers X-User-Id, X-User-Scopes, X-Session-Id, X-Primary-Connection are set
 * - Excluded paths (/api/v1/auth/**, /api/v1/admin/auth/**, /actuator/**): relay skipped
 * - Expired/invalid JWT: proceeds unauthenticated (no short-circuit)
 * - No Authorization header: proceeds unauthenticated
 * - JwtService errors (expired, signature, malformed): proceed unauthenticated
 */
class JwtAuthenticationHeaderRelayTest {

    private JwtAuthenticationHeaderRelay filter;
    private JwtService jwtService;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = mock(JwtService.class);

        filter = new JwtAuthenticationHeaderRelay();
        setField(filter, "jwtService", jwtService);

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ServerWebExchange buildExchange(String path, String auth) {
        var request = MockServerHttpRequest.get(path);
        if (auth != null) {
            request.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return MockServerWebExchange.from(request);
    }

    // ======================================================================
    // Excluded paths
    // ======================================================================

    @Test
    @DisplayName("/api/v1/auth/login — no JWT relay")
    void authLogin_skipped() {
        ServerWebExchange exchange = buildExchange("/api/v1/auth/login", "Bearer token");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("/api/v1/auth/otp — no JWT relay")
    void authOtp_skipped() {
        ServerWebExchange exchange = buildExchange("/api/v1/auth/otp", "Bearer token");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("/api/v1/auth/refresh — no JWT relay")
    void authRefresh_skipped() {
        ServerWebExchange exchange = buildExchange("/api/v1/auth/refresh", "Bearer token");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("/api/v1/admin/auth/login — no JWT relay")
    void adminAuthLogin_skipped() {
        ServerWebExchange exchange = buildExchange("/api/v1/admin/auth/login", "Bearer token");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("/actuator/health — no JWT relay")
    void actuatorHealth_skipped() {
        ServerWebExchange exchange = buildExchange("/actuator/health", "Bearer token");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    // ======================================================================
    // No Authorization header
    // ======================================================================

    @Test
    @DisplayName("No Authorization header → proceeds unauthenticated")
    void noAuthHeader_proceedsUnauthenticated() {
        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", null);

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Empty Authorization header → proceeds unauthenticated")
    void emptyAuthHeader_proceedsUnauthenticated() {
        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Bearer with empty token → proceeds unauthenticated")
    void emptyBearerToken_proceedsUnauthenticated() {
        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer   ");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Basic auth (not Bearer) → proceeds unauthenticated")
    void basicAuth_proceedsUnauthenticated() {
        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Basic dXNlcjpwYXNz");

        filter.filter(exchange, chain).block();

        verifyNoInteractions(jwtService);
    }

    // ======================================================================
    // Valid JWT — headers are set
    // ======================================================================

    @Test
    @DisplayName("Valid JWT → X-User-Id, X-User-Scopes, X-Session-Id are set")
    void validJwt_setsHeaders() throws Exception {
        String token = "valid.jwt.token";
        JwtService.JwtClaims claims = JwtService.JwtClaims.builder()
            .subject("user-456")
            .tenantId("dialog-lk")
            .scope("customer:read customer:write")
            .sessionId("sess-xyz")
            .primaryConnection("conn-789")
            .build();
        when(jwtService.validate(token)).thenReturn(claims);

        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer " + token);

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            HttpHeaders headers = ex.getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-456");
            assertThat(headers.getFirst("X-User-Scopes")).isEqualTo("customer:read customer:write");
            assertThat(headers.getFirst("X-Session-Id")).isEqualTo("sess-xyz");
            assertThat(headers.getFirst("X-Primary-Connection")).isEqualTo("conn-789");
            return true;
        }));
    }

    @Test
    @DisplayName("Valid JWT with null claims → empty strings used (no NPE)")
    void validJwt_nullClaims_noNpe() throws Exception {
        String token = "valid.jwt.token";
        JwtService.JwtClaims claims = JwtService.JwtClaims.builder()
            .subject(null)  // null subject
            .tenantId(null)
            .scope(null)
            .sessionId(null)
            .primaryConnection(null)
            .build();
        when(jwtService.validate(token)).thenReturn(claims);

        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer " + token);

        // Should not throw NPE
        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Valid JWT → exchange attribute 'jwt.claims' is set")
    void validJwt_setsExchangeAttribute() throws Exception {
        String token = "valid.jwt.token";
        JwtService.JwtClaims claims = JwtService.JwtClaims.builder()
            .subject("user-123")
            .build();
        when(jwtService.validate(token)).thenReturn(claims);

        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer " + token);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute("jwt.claims")).isSameAs(claims);
    }

    // ======================================================================
    // JWT validation failures — proceed unauthenticated
    // ======================================================================

    @Test
    @DisplayName("Expired JWT → proceeds unauthenticated (no short-circuit)")
    void expiredJwt_proceedsUnauthenticated() throws Exception {
        String token = "expired.jwt";
        when(jwtService.validate(token)).thenThrow(new ExpiredJwtException(null, null, "Expired"));

        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer " + token);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Invalid signature → proceeds unauthenticated")
    void invalidSignature_proceedsUnauthenticated() throws Exception {
        String token = "bad-signature.jwt";
        when(jwtService.validate(token)).thenThrow(new SignatureException("Invalid signature"));

        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer " + token);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("Malformed JWT → proceeds unauthenticated")
    void malformedJwt_proceedsUnauthenticated() throws Exception {
        String token = "not-a-jwt";
        when(jwtService.validate(token)).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed"));

        ServerWebExchange exchange = buildExchange("/api/v1/customers/me", "Bearer " + token);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    // ======================================================================
    // Filter order
    // ======================================================================

    @Test
    @DisplayName("Filter order is HIGHEST_PRECEDENCE + 250")
    void correctOrder() {
        assertThat(filter.getOrder()).isEqualTo(
            org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 250);
    }
}
