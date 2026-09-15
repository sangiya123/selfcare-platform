package com.selfcare.platform.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT token creation and validation.
 *
 * Used by customer-identity-service and admin-identity-service to issue JWTs.
 * RS256 signing with rotated key pair.
 *
 * NOTE: Token policy (ADR-011) is OPEN. The current implementation uses
 * conservative defaults. Security review required before production.
 */
@Slf4j
public class JwtService {

    private final String issuer;
    private final String audience;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;
    private final RSAPrivateKey signingKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    @Builder
    public JwtService(String issuer, String audience, long accessTokenSeconds, long refreshTokenSeconds,
                      RSAPrivateKey signingKey, RSAPublicKey publicKey, String keyId) {
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenSeconds = accessTokenSeconds;
        this.refreshTokenSeconds = refreshTokenSeconds;
        this.signingKey = signingKey;
        this.publicKey = publicKey;
        this.keyId = keyId != null ? keyId : "default";
    }

    /**
     * Create a new RSA key pair for signing/validation.
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * Issue an access token for a user session.
     */
    public String issueAccessToken(String subject, String tenantId, String sessionId,
                                   String primaryConnection, String environment, String scope) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenSeconds);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", tenantId);
        claims.put("session_id", sessionId);
        claims.put("primary_connection", primaryConnection);
        claims.put("environment", environment);
        claims.put("scope", scope);
        claims.put("token_type", "access");
        claims.put("correlation_id", UUID.randomUUID().toString());

        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .header().keyId(keyId).and()
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Issue a refresh token (longer-lived).
     */
    public String issueRefreshToken(String subject, String tenantId, String sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(refreshTokenSeconds);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", tenantId);
        claims.put("session_id", sessionId);
        claims.put("token_type", "refresh");

        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .header().keyId(keyId).and()
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Validate and parse a JWT.
     */
    public JwtClaims validate(String token) {
        try {
            JwtParser parser = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build();
            Jws<Claims> jws = parser.parseSignedClaims(token);
            Claims c = jws.getPayload();
            return JwtClaims.builder()
                    .subject(c.getSubject())
                    .issuer(c.getIssuer())
                    .audience(c.getAudience().toString())
                    .issuedAt(c.getIssuedAt().toInstant())
                    .expiration(c.getExpiration().toInstant())
                    .tenantId(c.get("tenant_id", String.class))
                    .sessionId(c.get("session_id", String.class))
                    .primaryConnection(c.get("primary_connection", String.class))
                    .environment(c.get("environment", String.class))
                    .scope(c.get("scope", String.class))
                    .tokenType(c.get("token_type", String.class))
                    .correlationId(c.get("correlation_id", String.class))
                    .jti(c.getId())
                    .build();
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Convert RSAPublicKey to PEM string for JWKS publication.
     */
    public String getPublicKeyAsPem() {
        String base64 = java.util.Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }

    /**
     * Get key ID for JWKS.
     */
    public String getKeyId() {
        return keyId;
    }

    /**
     * Get the RSA public key.
     */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    @Value
    @Builder
    public static class JwtClaims {
        String subject;
        String issuer;
        String audience;
        java.time.Instant issuedAt;
        java.time.Instant expiration;
        String tenantId;
        String sessionId;
        String primaryConnection;
        String environment;
        String scope;
        String tokenType;
        String correlationId;
        String jti;
    }
}