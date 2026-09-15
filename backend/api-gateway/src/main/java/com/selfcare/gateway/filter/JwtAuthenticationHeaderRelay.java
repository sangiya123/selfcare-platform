package com.selfcare.gateway.filter;

import com.selfcare.platform.common.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global pre-filter that extracts a bearer JWT from the {@code Authorization}
 * header, validates it locally (RS256 signature verification via
 * {@link JwtService}), and propagates user-identity claims as downstream
 * headers:
 *
 * <ul>
 *   <li>{@code X-User-Id} &mdash; JWT {@code sub} claim (subject / user identifier)</li>
 *   <li>{@code X-User-Scopes} &mdash; JWT {@code scope} claim (space-delimited RBAC scopes)</li>
 *   <li>{@code X-Session-Id} &mdash; JWT {@code session_id} claim</li>
 *   <li>{@code X-Tenant-Id} &mdash; JWT {@code tenant_id} claim (mirrors the header for
 *       downstream services that parse claims)</li>
 *   <li>{@code X-Primary-Connection} &mdash; JWT {@code primary_connection} claim</li>
 * </ul>
 *
 * <p>This replaces the trust of any client-supplied {@code X-User-Id} /
 * {@code X-Session-Id} headers: downstream services receive identity exclusively
 * from the validated JWT, never from the client.
 *
 * <p>Execution order: runs after {@link JwtRateLimitExclusionFilter} and
 * {@link TenantRoutingGatewayFilterFactory} but before the route filters
 * so that identity headers are present for downstream service invocation.
 *
 * <p>Paths excluded from JWT relay:
 * <ul>
 *   <li>{@code /api/v1/auth/**} &mdash; login endpoints have no JWT yet</li>
 *   <li>{@code /api/v1/admin/auth/**} &mdash; admin login same reason</li>
 *   <li>{@code /actuator/**} &mdash; health / metrics endpoints</li>
 * </ul>
 *
 * <p>Token expiry ({@link ExpiredJwtException}) does NOT short-circuit the request.
 * The request proceeds unauthenticated so that downstream services can return
 * a structured 401 with their own correlation IDs.  All other validation failures
 * ({@link SignatureException}, {@link MalformedJwtException}, etc.) are treated
 * the same way.
 *
 * @see TenantRoutingGatewayFilterFactory
 * @see JwtRateLimitExclusionFilter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationHeaderRelay implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_SCOPES = "X-User-Scopes";
    private static final String HEADER_SESSION_ID = "X-Session-Id";
    private static final String HEADER_PRIMARY_CONNECTION = "X-Primary-Connection";

    /** Paths that are exempt from JWT relay. */
    private static final String AUTH_PATH = "/api/v1/auth";
    private static final String ADMIN_AUTH_PATH = "/api/v1/admin/auth";

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // ----------------------------------------------------------------
        // Skip relay for unauthenticated endpoints
        // ----------------------------------------------------------------
        if (isExcludedPath(path)) {
            log.trace("JWT relay skipped for excluded path: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.trace("No bearer token found for path: {}", path);
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            log.trace("Empty bearer token for path: {}", path);
            return chain.filter(exchange);
        }

        // ----------------------------------------------------------------
        // Validate JWT locally (RS256 signature check)
        // ----------------------------------------------------------------
        try {
            JwtService.JwtClaims claims = jwtService.validate(token);

            ServerHttpRequest mutated = request.mutate()
                    .header(HEADER_USER_ID, claims.getSubject() != null ? claims.getSubject() : "")
                    .header(HEADER_SESSION_ID, nvl(claims.getSessionId()))
                    .header(HEADER_USER_SCOPES, nvl(claims.getScope()))
                    .header(HEADER_PRIMARY_CONNECTION, nvl(claims.getPrimaryConnection()))
                    .build();

            // Enrich the exchange with claims for downstream use (e.g. TenantKeyResolver)
            exchange.getAttributes().put("jwt.claims", claims);

            log.debug("JWT relay: user={} tenant={} scopes={} path={}",
                    claims.getSubject(), claims.getTenantId(), claims.getScope(), path);

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (ExpiredJwtException e) {
            // Token is expired — log and proceed unauthenticated.
            // Downstream services will return their own 401 with structured body.
            log.info("JWT expired for request {} {}: {}", request.getMethod(), path, e.getMessage());
            return chain.filter(exchange);

        } catch (SignatureException e) {
            log.warn("JWT signature invalid for request {} {}: {}", request.getMethod(), path, e.getMessage());
            return chain.filter(exchange);

        } catch (MalformedJwtException e) {
            log.warn("JWT malformed for request {} {}: {}", request.getMethod(), path, e.getMessage());
            return chain.filter(exchange);

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation error for request {} {}: {}", request.getMethod(), path, e.getMessage());
            return chain.filter(exchange);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true for paths that should not participate in JWT relay.
     * These are login endpoints (no token present yet) and actuator paths.
     */
    private boolean isExcludedPath(String path) {
        return path.startsWith(AUTH_PATH)
                || path.startsWith(ADMIN_AUTH_PATH)
                || path.startsWith("/actuator");
    }

    /**
     * Returns the value or empty string if null.  Never returns null so that
     * {@link ServerHttpRequest.Builder#header(String, String)} never receives
     * a null value (which would remove the header).
     */
    private static String nvl(String value) {
        return value != null ? value : "";
    }

    /**
     * Runs late among global filters so that {@link JwtRateLimitExclusionFilter}
     * and {@link TenantRoutingGatewayFilterFactory} have already run and set
     * their context.  Negative values run before route filters; positive values
     * run after.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 250; // ~-9750
    }
}
