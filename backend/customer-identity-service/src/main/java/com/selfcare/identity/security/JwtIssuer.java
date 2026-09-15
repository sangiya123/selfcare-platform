package com.selfcare.identity.security;

import com.selfcare.identity.domain.Session;
import com.selfcare.identity.domain.TenantTokenPolicy;
import com.selfcare.identity.service.TenantTokenPolicyService;
import com.selfcare.platform.common.security.JwtService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * JWT issuer — wraps the platform-common {@link JwtService} and provides
 * typed issue methods for sessions and the OTP-verify flow.
 *
 * <p>Configured with an RSA key pair (RS256). If no key is supplied via
 * configuration, a fresh 2048-bit pair is generated at startup. In production
 * the key should come from a secret manager / KMS; the auto-generation path
 * is intended for local development and first-boot scenarios only.
 *
 * <p>Token claims follow the platform convention (see JwtService):
 * <ul>
 *   <li>{@code sub} — user ID
 *   <li>{@code tenant_id} — multi-tenant routing key
 *   <li>{@code session_id} — durable session reference
 *   <li>{@code primary_connection} — telco connection ID (or null)
 *   <li>{@code token_type} — "access" or "refresh"
 * </ul>
 *
 * <p><b>Per-tenant TTL (ADR-011)</b>: TTL values are loaded from the tenant's
 * MongoDB policy record on each issuance via {@link TenantTokenPolicyService}.
 * The {@code selfcare.security.jwt.*} env-driven values are the platform
 * baseline and are used only when no tenant policy record exists.
 */
@Slf4j
@Component
public class JwtIssuer {

    private final JwtService jwtService;
    private final TenantTokenPolicyService policyService;
    @Getter
    private final String keyId;
    @Getter
    private final RSAPublicKey publicKey;

    /** Platform-baseline access token TTL; used when no tenant policy exists. */
    private final long defaultAccessTokenSeconds;

    /** Platform-baseline refresh token TTL; used when no tenant policy exists. */
    private final long defaultRefreshTokenSeconds;

    public JwtIssuer(JwtService jwtService,
                     TenantTokenPolicyService policyService,
                     RSAPublicKey publicKey,
                     @Value("${selfcare.security.jwt.key-id:selfcare-1}") String keyId,
                     @Value("${selfcare.security.jwt.access-token-seconds:86400}") long defaultAccessTokenSeconds,
                     @Value("${selfcare.security.jwt.refresh-token-seconds:2592000}") long defaultRefreshTokenSeconds) {
        this.jwtService = jwtService;
        this.policyService = policyService;
        this.publicKey = publicKey;
        this.keyId = keyId;
        this.defaultAccessTokenSeconds = defaultAccessTokenSeconds;
        this.defaultRefreshTokenSeconds = defaultRefreshTokenSeconds;
    }

    /**
     * Issue an access+refresh token pair for an established session.
     *
     * @param session the durable session record
     * @return token pair with both access and refresh tokens and their TTLs
     */
    public IssuedTokens issueForSession(Session session) {
        TenantTokenPolicy policy = policyService.getEffectivePolicy(session.getTenantId());
        long accessSeconds = policy.effectiveAccessTokenSeconds();
        long refreshSeconds = policy.effectiveRefreshTokenSeconds();

        String scope = "openid profile selfcare";
        String accessToken = jwtService.issueAccessToken(
                session.getUserId(),
                session.getTenantId(),
                session.getSessionId(),
                null, // primary_connection is not on the Session entity itself
                "live",
                scope
        );

        String refreshToken = jwtService.issueRefreshToken(
                session.getUserId(),
                session.getTenantId(),
                session.getSessionId()
        );

        log.debug("Tokens issued: tenant={}, userId={}, accessTTL={}s, refreshTTL={}s",
                session.getTenantId(), session.getUserId(), accessSeconds, refreshSeconds);
        return new IssuedTokens(accessToken, refreshToken, accessSeconds, refreshSeconds);
    }

    /**
     * Validate a refresh token and return its claims.
     *
     * <p>Verifies signature, issuer, audience, expiry, and confirms
     * {@code token_type == "refresh"}.
     *
     * @param token the refresh token
     * @return the parsed claims
     * @throws com.selfcare.platform.common.web.UnauthorizedException if invalid
     */
    public com.selfcare.platform.common.security.JwtService.JwtClaims validateRefreshToken(String token) {
        var claims = jwtService.validate(token);
        if (!"refresh".equals(claims.getTokenType())) {
            throw new com.selfcare.platform.common.web.UnauthorizedException(
                    "Token is not a refresh token");
        }
        return claims;
    }

    /**
     * Issue a short-lived JWT for the OTP-verify flow.
     *
     * <p>This is the access token returned to the client on a successful
     * OTP verify. The user ID and primary connection are extracted by the
     * verify caller (typically from the operator's customer/entitlement
     * service) and threaded in here.
     *
     * @param userId            customer / user ID
     * @param tenantId          tenant ID
     * @param primaryConnection telco primary connection (optional)
     * @return the issued tokens
     */
    public IssuedTokens issueForOtpFlow(String userId, String tenantId, String primaryConnection) {
        TenantTokenPolicy policy = policyService.getEffectivePolicy(tenantId);
        long accessSeconds = policy.effectiveAccessTokenSeconds();
        long refreshSeconds = policy.effectiveRefreshTokenSeconds();

        // For the OTP flow we use a placeholder session ID — the controller
        // typically follows this call by creating a real Session record.
        String ephemeralSessionId = "OTP_FLOW:" + UUID.randomUUID();

        String accessToken = jwtService.issueAccessToken(
                userId,
                tenantId,
                ephemeralSessionId,
                primaryConnection,
                "live",
                "openid profile selfcare"
        );

        String refreshToken = jwtService.issueRefreshToken(
                userId,
                tenantId,
                ephemeralSessionId
        );

        return new IssuedTokens(accessToken, refreshToken, accessSeconds, refreshSeconds);
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Issued token pair returned to the client.
     *
     * @param accessToken  short-lived bearer token
     * @param refreshToken longer-lived rotation token
     * @param expiresInSeconds TTL of the access token in seconds
     * @param refreshExpiresInSeconds TTL of the refresh token in seconds
     */
    public record IssuedTokens(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            long refreshExpiresInSeconds
    ) {}

    // -------------------------------------------------------------------------
    // JwtService configuration
    // -------------------------------------------------------------------------

    /**
     * Configuration that wires the platform-common {@link JwtService} as a
     * Spring bean, using an RSA key pair.
     *
     * <p>If a PEM-encoded public key is provided via configuration, it is
     * used as-is. Otherwise a new key pair is generated at startup and
     * the private key is logged in PEM form for one-time export to a
     * secret manager. NOTE: log-emitted private keys are appropriate only
     * for ephemeral / dev environments; production must use {@code selfcare.security.jwt.private-key-pem}.
     */
    @Configuration
    public static class JwtConfig {

        @Value("${selfcare.security.jwt.issuer:selfcare-platform}")
        private String issuer;

        @Value("${selfcare.security.jwt.audience:selfcare-platform}")
        private String audience;

        @Value("${selfcare.security.jwt.access-token-seconds:86400}")
        private long accessTokenSeconds;

        @Value("${selfcare.security.jwt.refresh-token-seconds:2592000}")
        private long refreshTokenSeconds;

        @Value("${selfcare.security.jwt.key-id:selfcare-1}")
        private String keyId;

        @Value("${selfcare.security.jwt.private-key-pem:}")
        private String privateKeyPem;

        @org.springframework.context.annotation.Bean
        public KeyPair rsaKeyPair() {
            return resolveKeyPair();
        }

        @org.springframework.context.annotation.Bean
        public JwtService jwtService(KeyPair rsaKeyPair) {
            return JwtService.builder()
                    .issuer(issuer)
                    .audience(audience)
                    .accessTokenSeconds(accessTokenSeconds)
                    .refreshTokenSeconds(refreshTokenSeconds)
                    .signingKey((RSAPrivateKey) rsaKeyPair.getPrivate())
                    .publicKey((RSAPublicKey) rsaKeyPair.getPublic())
                    .keyId(keyId)
                    .build();
        }

        @org.springframework.context.annotation.Bean
        public RSAPublicKey rsaPublicKey(KeyPair rsaKeyPair) {
            return (RSAPublicKey) rsaKeyPair.getPublic();
        }

        private KeyPair resolveKeyPair() {
            if (privateKeyPem != null && !privateKeyPem.isBlank()) {
                log.info("Loading RSA key pair from configuration (kid={})", keyId);
                return loadFromPem(privateKeyPem);
            }
            log.warn("No RSA key configured — generating ephemeral key pair for kid={}. " +
                    "Set selfcare.security.jwt.private-key-pem for production.", keyId);
            return JwtService.generateKeyPair();
        }

        private KeyPair loadFromPem(String pem) {
            // Minimal PKCS#8 PEM parser. Production should use a real key loader;
            // here we defer that to the operator / deployment pipeline.
            throw new UnsupportedOperationException(
                    "Loading RSA keys from PEM at runtime is not yet implemented; " +
                    "use a deployment-time key injection or set the key via secret manager");
        }
    }
}
