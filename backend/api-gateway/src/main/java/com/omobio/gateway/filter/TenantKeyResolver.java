package com.omobio.gateway.filter;

import com.omobio.platform.common.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.util.Optional;

/**
 * Key resolver for Spring Cloud Gateway's {@link org.springframework.cloud.gateway.filter.ratelimit.RateLimiter}.
 *
 * <p>Builds the Redis rate-limit key as:
 * <ul>
 *   <li>{@code {tenantId}:{userId}} &mdash; authenticated requests (JWT present and valid)</li>
 *   <li>{@code {tenantId}:{clientIp}} &mdash; unauthenticated requests (no / invalid JWT)</li>
 * </ul>
 *
 * <p>The separator {@code :} matches Spring Cloud Gateway's default Redis key prefix
 * so the full Redis key becomes {@code request_rate_limit:{tenantId}:{userId-or-ip}}.
 *
 * <p>Implementation notes:
 * <ul>
 *   <li>Reads the JWT directly from the {@code Authorization} header; no JWKS round-trip
 *       (local signature validation via the RSA public key injected as a bean)</li>
 *   <li>Gracefully falls back to IP-based keying when the token is absent or invalid</li>
 *   <li>Tenant ID is read from the {@code X-Tenant-Id} header, which is guaranteed
 *       non-blank at this point because {@link TenantRoutingGatewayFilterFactory}
 *       short-circuits with 400 before this resolver is reached</li>
 * </ul>
 *
 * <p>Bean name: {@code tenantKeyResolver} &mdash; referenced in application.yml as
 * {@code key-resolver: "#{@tenantKeyResolver}"}.
 *
 * @see TenantRoutingGatewayFilterFactory
 * @see <a href="https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/ratelimiter.html">Spring Cloud Gateway RateLimiter docs</a>
 */
@Slf4j
@Component("tenantKeyResolver")
@RequiredArgsConstructor
public class TenantKeyResolver implements KeyResolver {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * JwtService bean configured in {@link GatewayJwtConfig}.  Declared as
     * {@code JwtService} (interface-like concrete class) rather than a supplier
     * so this class remains straightforward.
     */
    private final JwtService jwtService;

    @Value("${omobio.security.jwt.issuer:}")
    private String expectedIssuer;

    @Value("${omobio.security.jwt.audience:omobio-selfcare-platform}")
    private String expectedAudience;

    /**
     * Resolves the rate-limit key for a single exchange.
     *
     * @param exchange the current server web exchange (never {@code null})
     * @return a {@link Mono} emitting the key string
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        // Tenant ID is guaranteed to be set by TenantRoutingGatewayFilterFactory
        String tenantId = request.getHeaders().getFirst(TENANT_ID_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "UNKNOWN";
        }

        // Attempt JWT extraction and validation
        Optional<String> userId = extractUserIdFromJwt(request);

        String key;
        if (userId.isPresent()) {
            key = tenantId + ":" + userId.get();
        } else {
            key = tenantId + ":" + resolveClientIp(request);
        }

        log.trace("Rate-limit key resolved: {}", key);
        return Mono.just(key);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the JWT from the {@code Authorization: Bearer <token>} header and
     * returns the {@code sub} (subject = userId) claim if the token is valid.
     *
     * @return {@code Optional.of(userId)} if the token is present and valid;
     *         {@code Optional.empty()} if no token, token is expired, or signature
     *         is invalid
     */
    private Optional<String> extractUserIdFromJwt(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String token = auth.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }

        try {
            JwtService.JwtClaims claims = jwtService.validate(token);
            if (claims != null && claims.getSubject() != null) {
                log.trace("JWT validated for user={} tenant={}",
                        claims.getSubject(), claims.getTenantId());
                return Optional.of(claims.getSubject());
            }
        } catch (JwtException e) {
            log.debug("JWT validation failed in TenantKeyResolver, falling back to IP: {}",
                    e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Returns the client IP, honouring {@code X-Forwarded-For} for proxied requests.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String xFwd = request.getHeaders().getFirst("X-Forwarded-For");
        if (xFwd != null && !xFwd.isBlank()) {
            return xFwd.split(",")[0].trim();
        }
        String xReal = request.getHeaders().getFirst("X-Real-IP");
        if (xReal != null && !xReal.isBlank()) {
            return xReal.trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
