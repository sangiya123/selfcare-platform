package com.omobio.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global pre-filter that marks requests to the authentication surface
 * ({@code /api/v1/auth/**}) so that rate limiting is skipped for them.
 *
 * <p>Rationale: login and token-refresh endpoints have a fundamentally different
 * rate-limit model from the rest of the platform:
 * <ul>
 *   <li>Users do not yet have a valid session JWT on these paths</li>
 *   <li>They are subject to bot-detection / CAPTCHA-based controls at the
 *       identity-service layer, not at the gateway</li>
 *   <li>Applying per-tenant, per-IP limits here would unfairly penalise shared
 *       corporate networks where many users appear from the same IP</li>
 * </ul>
 *
 * <p>This filter runs at {@link Ordered#HIGHEST_PRECEDENCE} + 200 so it executes
 * before the {@link org.springframework.cloud.gateway.filter.ratelimit.RateLimiter}
 * filter (which runs at ~100) but after tenant routing has been established.
 *
 * <p>Implementation: sets the exchange attribute {@code gateway.rate-limit.skip}
 * to {@code true} for matching paths.  The gateway's
 * {@code spring.cloud.gateway.filter.request-ratelimiter} looks for this
 * attribute and returns {@link reactor.core.publisher.Mono#empty()} (allow through
 * without consuming a token) when it is present.
 *
 * @see JwtAuthenticationHeaderRelay
 * @see TenantRoutingGatewayFilterFactory
 */
@Slf4j
@Component
public class JwtRateLimitExclusionFilter implements GlobalFilter, Ordered {

    /** Exchange attribute key used to signal rate-limit skip. */
    public static final String RATE_LIMIT_SKIP_ATTR = "gateway.rate-limit.skip";

    /** Path prefix that is excluded from rate limiting. */
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (path.startsWith(AUTH_PATH_PREFIX)) {
            log.debug("Rate-limit exclusion applied for auth path: {}", path);
            exchange.getAttributes().put(RATE_LIMIT_SKIP_ATTR, true);
        }

        return chain.filter(exchange);
    }

    /**
     * Run early — before the RateLimiter filter (~100) but after any header
     * normalisation filters.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
