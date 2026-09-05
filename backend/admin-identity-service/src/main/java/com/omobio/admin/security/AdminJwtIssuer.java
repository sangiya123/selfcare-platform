package com.omobio.admin.security;

import com.omobio.platform.common.security.JwtService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT issuer specifically for admin authentication.
 *
 * Uses a separate RSA key pair and configuration from the customer JWT issuer,
 * so admin tokens are clearly distinguished and have their own signing keys.
 *
 * <p>
 * Token policy (ADR-011) is OPEN. Defaults: 1-hour access, 30-day refresh.
 * </p>
 */
@Slf4j
@Component
public class AdminJwtIssuer {

    @Value("${omobio.security.admin-jwt.issuer:omobio-admin-identity}")
    private String issuer;

    @Value("${omobio.security.admin-jwt.audience:omobio-selfcare-platform}")
    private String audience;

    @Value("${omobio.security.admin-jwt.access-token-seconds:3600}")
    private long accessTokenSeconds;

    @Value("${omobio.security.admin-jwt.refresh-token-seconds:2592000}")
    private long refreshTokenSeconds;

    private JwtService jwtService;
    private KeyPair keyPair;

    /**
     * Initialize the key pair and JwtService.
     */
    @PostConstruct
    public void init() {
        try {
            this.keyPair = JwtService.generateKeyPair();
            this.jwtService = JwtService.builder()
                    .issuer(issuer)
                    .audience(audience)
                    .accessTokenSeconds(accessTokenSeconds)
                    .refreshTokenSeconds(refreshTokenSeconds)
                    .signingKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                    .publicKey((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .keyId("admin-key-1")
                    .build();
            log.info("Admin JWT issuer initialized: issuer={}, audience={}, accessTTL={}s, refreshTTL={}s",
                    issuer, audience, accessTokenSeconds, refreshTokenSeconds);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AdminJwtIssuer", e);
        }
    }

    /**
     * Issue an access token for an admin user.
     */
    public String issueAccessToken(String adminUserId, String tenantId, String sessionId,
                                   String role, String environment) {
        // Build claims via issueAccessToken (it accepts generic claims via scope)
        // We need to include the admin role - we use scope for this purpose
        String scope = "admin:" + role;
        return jwtService.issueAccessToken(adminUserId, tenantId, sessionId,
                "admin", environment, scope);
    }

    /**
     * Issue a refresh token.
     */
    public String issueRefreshToken(String adminUserId, String tenantId, String sessionId) {
        return jwtService.issueRefreshToken(adminUserId, tenantId, sessionId);
    }

    /**
     * Validate a token.
     */
    public JwtService.JwtClaims validate(String token) {
        return jwtService.validate(token);
    }

    /**
     * Get the public key as PEM.
     */
    public String getPublicKeyAsPem() {
        return jwtService.getPublicKeyAsPem();
    }

    /**
     * Get the key ID.
     */
    public String getKeyId() {
        return jwtService.getKeyId();
    }
}
