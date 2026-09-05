package com.omobio.gateway.filter;

import com.omobio.platform.common.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantKeyResolver.
 *
 * Verifies Redis rate-limit key resolution:
 * - Authenticated: {tenantId}:{userId}
 * - Unauthenticated: {tenantId}:{clientIp}
 *
 * Key source priority: JWT sub claim > X-Forwarded-For > X-Real-IP > remoteAddress
 */
class TenantKeyResolverTest {

    private TenantKeyResolver resolver;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = mock(JwtService.class);

        resolver = new TenantKeyResolver();
        // Inject mock JwtService via reflection (no-arg constructor + @RequiredArgsConstructor)
        setField(resolver, "jwtService", jwtService);

        // Set @Value fields
        setField(resolver, "expectedIssuer", "omobio-platform");
        setField(resolver, "expectedAudience", "omobio-selfcare-platform");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ======================================================================
    // Authenticated requests — JWT validated → user-based key
    // ======================================================================

    @Test
    @DisplayName("Valid JWT → key is tenant:userId")
    void validJwt_userBasedKey() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.valid.signature";
        JwtService.JwtClaims claims = JwtService.JwtClaims.builder()
            .subject("user-123")
            .tenantId("dialog-lk")
            .scope("customer:read customer:write")
            .sessionId("sess-abc")
            .build();
        when(jwtService.validate(token)).thenReturn(claims);

        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header("X-Tenant-Id", "dialog-lk")
            .header("Host", "dialog-lk.omobio.io")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).isEqualTo("dialog-lk:user-123"))
            .verifyComplete();

        verify(jwtService).validate(token);
    }

    @Test
    @DisplayName("Expired JWT → falls back to IP-based key")
    void expiredJwt_fallsBackToIp() throws Exception {
        String token = "expired-token";
        when(jwtService.validate(token)).thenThrow(new JwtException("Token expired"));

        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header("X-Tenant-Id", "hutch-lk")
            .header("Host", "hutch-lk.omobio.io")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).startsWith("hutch-lk:"))
            .verifyComplete();
    }

    @Test
    @DisplayName("Malformed JWT → falls back to IP-based key")
    void malformedJwt_fallsBackToIp() throws Exception {
        String token = "not-even-close";
        when(jwtService.validate(token)).thenThrow(new JwtException("Malformed"));

        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header("X-Tenant-Id", "airtel-lk")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).startsWith("airtel-lk:"))
            .verifyComplete();
    }

    @Test
    @DisplayName("Missing tenant ID → defaults to UNKNOWN")
    void missingTenantId_unknown() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("Host", "unknown.omobio.io")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).startsWith("UNKNOWN:"))
            .verifyComplete();
    }

    // ======================================================================
    // Unauthenticated requests — IP-based key
    // ======================================================================

    @Test
    @DisplayName("No Authorization header → IP-based key from X-Forwarded-For")
    void noAuth_xForwardedForKey() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/public/news")
            .header("X-Tenant-Id", "dialog-lk")
            .header("X-Forwarded-For", "203.0.113.50, 10.0.0.1")
            .header("Host", "dialog-lk.omobio.io")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).isEqualTo("dialog-lk:203.0.113.50"))
            .verifyComplete();
    }

    @Test
    @DisplayName("No Authorization header → IP-based key from X-Real-IP")
    void noAuth_xRealIpKey() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/public/news")
            .header("X-Tenant-Id", "hutch-lk")
            .header("X-Real-IP", "198.51.100.25")
            .header("Host", "hutch-lk.omobio.io")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).isEqualTo("hutch-lk:198.51.100.25"))
            .verifyComplete();
    }

    @Test
    @DisplayName("No proxy headers → uses remote address")
    void noProxyHeaders_remoteAddress() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/public/news")
            .header("X-Tenant-Id", "airtel-lk")
            .header("Host", "airtel-lk.omobio.io")
            .remoteAddress(new java.net.InetSocketAddress("93.184.216.34", 8080))
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).isEqualTo("airtel-lk:93.184.216.34"))
            .verifyComplete();
    }

    // ======================================================================
    // Edge cases
    // ======================================================================

    @Test
    @DisplayName("Blank Authorization header → unauthenticated")
    void blankAuthHeader_unauthenticated() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ")
            .header("X-Tenant-Id", "dialog-lk")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).startsWith("dialog-lk:"))
            .verifyComplete();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Authorization without Bearer prefix → unauthenticated")
    void noBearerPrefix_unauthenticated() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            .header("X-Tenant-Id", "dialog-lk")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).startsWith("dialog-lk:"))
            .verifyComplete();

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Whitespace tenant ID → UNKNOWN")
    void whitespaceTenantId_unknown() {
        ServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/customers/me")
            .header("X-Tenant-Id", "   ")
            .build();

        StepVerifier.create(resolver.resolve(request))
            .assertNext(key -> assertThat(key).startsWith("UNKNOWN:"))
            .verifyComplete();
    }
}
