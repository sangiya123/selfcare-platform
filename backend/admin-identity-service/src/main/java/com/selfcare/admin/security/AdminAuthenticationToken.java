package com.selfcare.admin.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Spring Security authentication token for an admin user.
 * Extends AbstractAuthenticationToken to integrate with @EnableMethodSecurity.
 */
public class AdminAuthenticationToken extends AbstractAuthenticationToken {

    private final String adminId;
    private final String tenantId;
    private final String sessionId;
    private final Jwt jwt;

    public AdminAuthenticationToken(String adminId, String tenantId, String sessionId,
                                     Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.adminId = adminId;
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return jwt.getTokenValue(); }

    @Override
    public Object getPrincipal() { return adminId; }

    public String getTenantId() { return tenantId; }

    public String getSessionId() { return sessionId; }

    public Jwt getJwt() { return jwt; }
}
