package com.omobio.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-tenant token lifecycle policy.
 *
 * Stored in MongoDB (tenant_config database) as the source of truth per ADR-011.
 * Values are read on every token issuance — a policy change takes effect on
 * the next access-token or refresh-token refresh, not mid-flight.
 *
 * Defaults are enforced as hard limits (service refuses to issue tokens
 * exceeding the platform maximums even if the DB says otherwise).
 *
 * @see com.omobio.identity.service.TenantTokenPolicyService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenant_token_policies")
public class TenantTokenPolicy {

    @Id
    private String id;  // format: "tenant-token-policy:<tenantId>"

    /** The tenant this policy applies to. */
    private String tenantId;

    /**
     * Access token lifetime in seconds.
     * Platform maximum: 42 days (3,628,800 s).
     * Default: 86,400 s (24 hours).
     */
    @Builder.Default
    private long accessTokenSeconds = 86_400L;

    /**
     * Refresh token lifetime in seconds.
     * Platform maximum: 7 months (~18,144,000 s).
     * Default: 2,592,000 s (30 days).
     */
    @Builder.Default
    private long refreshTokenSeconds = 2_592_000L;

    /**
     * If true, refresh tokens are bound to the device that originally received them.
     * A refresh attempt from a different device requires re-authentication (OTP).
     */
    @Builder.Default
    private boolean deviceBindingRequired = true;

    /**
     * If true, refresh token rotation is enforced (every refresh invalidates
     * the previous token — already platform-wide, this flag is for documentation).
     */
    @Builder.Default
    private boolean rotationEnforced = true;

    /**
     * Payment amount threshold above which step-up is required.
     * Expressed in the tenant's billing currency units.
     */
    @Builder.Default
    private BigDecimal stepUpThreshold = new BigDecimal("10000.00");

    /**
     * Maximum inactive session duration before automatic logout (seconds).
     * 0 means no inactivity timeout.
     */
    @Builder.Default
    private long maxInactiveSessionSeconds = 1_800L;

    /**
     * Required when the policy exceeds platform defaults.
     * Explains the business justification for the longer TTL.
     */
    private String justification;

    /** Monotonically increasing version for optimistic locking. */
    @Builder.Default
    private int policyVersion = 1;

    private Instant updatedAt;
    private String updatedBy;

    // -------------------------------------------------------------------------
    // Platform limits (enforced as hard caps regardless of DB value)
    // -------------------------------------------------------------------------

    /** Maximum allowed access token TTL in seconds (42 days). */
    public static final long MAX_ACCESS_TOKEN_SECONDS = 42 * 24 * 60 * 60; // 3,628,800

    /** Maximum allowed refresh token TTL in seconds (~7 months). */
    public static final long MAX_REFRESH_TOKEN_SECONDS = 210 * 24 * 60 * 60; // 18,144,000

    /** Default access token TTL (24 hours). */
    public static final long DEFAULT_ACCESS_TOKEN_SECONDS = 24 * 60 * 60; // 86,400

    /** Default refresh token TTL (30 days). */
    public static final long DEFAULT_REFRESH_TOKEN_SECONDS = 30 * 24 * 60 * 60; // 2,592,000

    /**
     * Return the effective access token TTL, capped at the platform maximum.
     */
    public long effectiveAccessTokenSeconds() {
        return Math.min(accessTokenSeconds, MAX_ACCESS_TOKEN_SECONDS);
    }

    /**
     * Return the effective refresh token TTL, capped at the platform maximum.
     */
    public long effectiveRefreshTokenSeconds() {
        return Math.min(refreshTokenSeconds, MAX_REFRESH_TOKEN_SECONDS);
    }

    /**
     * Return the default platform policy for a tenant that has no explicit policy record.
     */
    public static TenantTokenPolicy defaults(String tenantId) {
        return TenantTokenPolicy.builder()
                .id("tenant-token-policy:" + tenantId)
                .tenantId(tenantId)
                .accessTokenSeconds(DEFAULT_ACCESS_TOKEN_SECONDS)
                .refreshTokenSeconds(DEFAULT_REFRESH_TOKEN_SECONDS)
                .deviceBindingRequired(true)
                .rotationEnforced(true)
                .stepUpThreshold(new BigDecimal("10000.00"))
                .maxInactiveSessionSeconds(1_800L)
                .policyVersion(1)
                .build();
    }
}
