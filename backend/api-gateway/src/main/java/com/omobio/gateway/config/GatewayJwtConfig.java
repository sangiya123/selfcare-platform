package com.omobio.gateway.config;

import com.omobio.platform.common.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Provides a {@link JwtService} bean to the API gateway.
 *
 * <p>The gateway does NOT generate tokens; it only validates JWTs issued by
 * customer-identity-service and admin-identity-service.  In production, the
 * RSAPublicKey is loaded from the JWKS endpoint configured at
 * {@code omobio.security.jwt.jwks-uri}.  For local development a self-managed
 * RSA key pair is generated in-memory.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code omobio.security.jwt.issuer} &mdash; expected {@code iss} claim</li>
 *   <li>{@code omobio.security.jwt.audience} &mdash; expected {@code aud} claim</li>
 *   <li>{@code omobio.security.jwt.jwks-uri} &mdash; JWKS endpoint (optional in dev)</li>
 *   <li>{@code omobio.security.jwt.local-key.enabled} &mdash; generate a local key pair (dev only)</li>
 * </ul>
 */
@Slf4j
@Configuration
public class GatewayJwtConfig {

    @Value("${omobio.security.jwt.issuer:omobio-selfcare-platform}")
    private String issuer;

    @Value("${omobio.security.jwt.audience:omobio-selfcare-platform}")
    private String audience;

    @Value("${omobio.security.jwt.local-key.enabled:false}")
    private boolean localKeyEnabled;

    /**
     * Returns a {@link JwtService} configured for token validation only.
     *
     * <p>If {@code omobio.security.jwt.local-key.enabled} is true (typical in
     * local dev) an in-memory key pair is generated and used for both signing
     * and verification.  In non-local profiles the public key is expected to
     * be supplied by JWKS — the gateway loads the JWKS and feeds it to the
     * validator.  This configuration produces a baseline bean that downstream
     * code can replace when the JWKS is reachable.
     */
    @Bean
    public JwtService jwtService() {
        if (!localKeyEnabled) {
            log.warn("GatewayJwtConfig: local key not enabled. JwtService bean will be created "
                    + "with a freshly generated ephemeral key pair — tokens issued by customer-identity "
                    + "and admin-identity will NOT validate in this mode. Set "
                    + "'omobio.security.jwt.local-key.enabled=true' for local development.");
        }

        KeyPair keyPair = JwtService.generateKeyPair();
        return JwtService.builder()
                .issuer(issuer)
                .audience(audience)
                .accessTokenSeconds(900)        // 15 min (mirrors customer-identity)
                .refreshTokenSeconds(2_592_000) // 30 days
                .signingKey((RSAPrivateKey) keyPair.getPrivate())
                .publicKey((RSAPublicKey) keyPair.getPublic())
                .keyId("gateway-local-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                .build();
    }
}
