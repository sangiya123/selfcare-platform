package com.selfcare.admin.security;

import com.selfcare.admin.service.RoleService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Converts a validated admin JWT into Spring Security authentication.
 *
 * <p>Admin tokens carry the admin role inside the {@code scope} claim in the
 * form {@code admin:&lt;ROLE&gt;} (e.g. {@code admin:SUPER_ADMIN}). This
 * converter maps that scope to a {@code ROLE_*} authority so that
 * {@code hasRole}/{@code hasAnyRole} rules work as expected.
 */
@Component
public class AdminAuthenticationConverter implements Converter<Jwt, AdminAuthenticationToken> {

    private final RoleService roleService;

    public AdminAuthenticationConverter(RoleService roleService) {
        this.roleService = roleService;
    }

    @Override
    public AdminAuthenticationToken convert(Jwt jwt) {
        String adminId = jwt.getSubject();
        String tenantId = jwt.getClaimAsString("tenant_id");
        String sessionId = jwt.getClaimAsString("session_id");
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("tenantId");
        }
        if (sessionId == null) {
            sessionId = jwt.getClaimAsString("sessionId");
        }

        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new AdminAuthenticationToken(adminId, tenantId, sessionId, jwt, authorities);
    }

    /**
     * Derive authorities from the {@code scope} claim (admin:&lt;ROLE&gt;) and,
     * for backwards compatibility, a {@code roles} claim list when present.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String token : scope.split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                if (token.startsWith("admin:")) {
                    String role = token.substring("admin:".length()).toUpperCase();
                    if (!role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                } else {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + token));
                }
            }
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                }
            }
        }

        return authorities;
    }
}