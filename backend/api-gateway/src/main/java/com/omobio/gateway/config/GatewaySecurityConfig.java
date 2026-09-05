package com.omobio.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Disables Spring Security for the API gateway.
 *
 * <p>Authentication and authorization on the OMOBIO platform are handled at
 * the downstream service level, not at the gateway.  Rationale:
 *
 * <ul>
 *   <li>The gateway already validates JWTs locally in
 *       {@link com.omobio.gateway.filter.JwtAuthenticationHeaderRelay} and
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
 *   <li>Tenant identity propagation ({@link com.omobio.gateway.filter.TenantRoutingGatewayFilterFactory})</li>
 *   <li>JWT validation and user-identity header relay
 *       ({@link com.omobio.gateway.filter.JwtAuthenticationHeaderRelay})</li>
 *   <li>Rate limiting and DDoS protection (Spring Cloud Gateway + Redis)</li>
 *   <li>Stripping of client-supplied identity headers</li>
 *   <li>Circuit breaking and request timeouts (Resilience4j)</li>
 * </ul>
 */
@Slf4j
@Configuration
public class GatewaySecurityConfig {

    /**
     * Permits all traffic through the gateway.  Downstream services enforce
     * their own authentication / authorisation against the same JWKS endpoint.
     */
    @Bean
    public SecurityFilterChain servletSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring API gateway: Spring Security disabled (auth handled at downstream services)");
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {
            })
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Reactive security filter chain is set to a no-op.  This prevents the
     * default {@code spring-boot-starter-webflux} auto-configuration from
     * activating a default chain that would block or redirect traffic before
     * the gateway can route it.
     */
    @Bean
    @Primary
    public SecurityWebFilterChain reactiveSecurityFilterChain(
            org.springframework.security.config.web.server.ServerHttpSecurity http) {
        log.info("Configuring API gateway reactive security: SecurityWebFilterChain disabled");
        return http
                .csrf(org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authz -> authz.anyExchange().permitAll())
                .build();
    }
}
