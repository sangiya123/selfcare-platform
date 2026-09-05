package com.omobio.admin.security;

import com.omobio.admin.service.RoleService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts a validated JWT into Spring Security authentication.
 * Used by the SecurityFilterChain to populate SecurityContext.
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
        String tenantId = jwt.getClaimAsString("tenantId");
        String sessionId = jwt.getClaimAsString("sessionId");

        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        Collection<GrantedAuthority> authorities = (roles == null ? java.util.List.<String>of() : roles)
            .stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
            .collect(Collectors.toList());

        return new AdminAuthenticationToken(adminId, tenantId, sessionId, jwt, authorities);
    }
}
