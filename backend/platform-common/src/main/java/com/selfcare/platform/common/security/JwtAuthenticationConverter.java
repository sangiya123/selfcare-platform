package com.selfcare.platform.common.security;

import com.selfcare.platform.common.tenant.TenantContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Extracts tenant, user, session info from JWT claims and populates TenantContext.
 *
 * Expected JWT claims:
 *   - sub: user ID
 *   - tenant_id: tenant identifier
 *   - session_id: session identifier
 *   - scope: space-separated scopes (customer, admin, etc.)
 *   - primary_connection: optional primary connection ID
 *   - environment: dev/qa/staging/prod
 */
public class JwtAuthenticationConverter
        implements org.springframework.core.convert.converter.Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Set tenant context from JWT
        TenantContext ctx = TenantContext.get();
        if (ctx == null) {
            ctx = new TenantContext();
        }

        ctx.setTenantId(jwt.getClaimAsString("tenant_id"));
        ctx.setUserId(jwt.getSubject());
        ctx.setSessionId(jwt.getClaimAsString("session_id"));
        ctx.setEnvironment(jwt.getClaimAsString("environment"));
        ctx.setCorrelationId(jwt.getClaimAsString("correlation_id"));
        TenantContext.set(ctx);

        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Stream.of(scope.split(" "))
            .flatMap(s -> {
                if (s.startsWith("admin:")) {
                    String role = s.substring("admin:".length()).toUpperCase();
                    if (role.isBlank()) {
                        return Stream.of((GrantedAuthority) new SimpleGrantedAuthority("SCOPE_admin"));
                    }
                    return Stream.of(
                            (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_admin"),
                            (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)
                    );
                }
                return Stream.of((GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + s));
            })
            .collect(Collectors.toList());
    }
}