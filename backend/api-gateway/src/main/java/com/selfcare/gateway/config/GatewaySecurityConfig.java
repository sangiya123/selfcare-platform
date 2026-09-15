package com.selfcare.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Disables Spring Security for the API gateway.
 *
 * <p>Authentication and authorization on the selfcare platform are handled at
 * the downstream service level, not at the gateway.  Rationale:
 *
 * <ul>
 *   <li>The gateway already validates JWTs locally in
 *       {@link com.selfcare.gateway.filter.JwtAuthenticationHeaderRelay} and
 *       propagates identity headers to downstream services.  Re-running
 *       Spring Security's resource-server filter chain on the gateway would
 *       duplicate that work and complicate routing (login, refresh, and other
 *       unauthenticated endpoints would have to be permit-all'd anyway).</li>
 *   <li>Per-route authorisation decisions (RBAC scopes, tenant membership) are
 *       a downstream concern: each service owns its own authorisation policy
 *       and re-validates the JWT against the same JWKS as the gateway.</li>
 *   <li>Stripping Spring Security from the gateway also avoids an unnecessary
 *       Servlet / Reactive filter conflict between {@code spring-boot-starter-webflux}
 *       (used by Spring Cloud Gateway) and {@code spring-security-oauth2-resource-server}
 *       (servlet-based).</li>
 * </ul>
 *
 * <p>Cross-cutting security concerns that DO live on the gateway:
 * <ul>
 *   <li>Tenant identity propagation ({@link com.selfcare.gateway.filter.TenantRoutingGatewayFilterFactory})</li>
 *   <li>JWT validation and user-identity header relay
 *       ({@link com.selfcare.gateway.filter.JwtAuthenticationHeaderRelay})</li>
 *   <li>Rate limiting and DDoS protection (Spring Cloud Gateway + Redis)</li>
 *   <li>Stripping of client-supplied identity headers</li>
 *   <li>Circuit breaking and request timeouts (Resilience4j)</li>
 * </ul>
 */
@Slf4j
@Configuration
public class GatewaySecurityConfig {

    /**
     * Reactive security filter chain is set to a no-op.  This prevents the
     * default {@code spring-boot-starter-webflux} auto-configuration from
     * activating a default chain that would block or redirect traffic before
     * the gateway can route it.
     */
    @Bean
    @Primary
    public SecurityWebFilterChain reactiveSecurityFilterChain() {
        log.info("Configuring API gateway reactive security: SecurityWebFilterChain disabled");
        return ServerHttpSecurity.http()
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authz -> authz.anyExchange().permitAll())
                .build();
    }
}
