package com.omobio.admin.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tenant-scoped auth & security settings.
 *
 * Persisted as a single row per tenant; loaded into the request path
 * of {@code /api/v1/admin/settings/auth/**} endpoints and into the
 * runtime path of customer-identity-service for enforcement.
 *
 * Sub-objects:
 *   - OtpPolicy: TOTP code length, expiry, attempts, lockout
 *   - OidcConfig: SSO/OIDC metadata (client secrets NEVER echoed)
 *   - SessionPolicy: access/refresh TTL, max sessions, device policy
 *   - RiskRule list: automation triggers
 *   - AllowedOrigins: CORS allow list
 */
@Entity
@Table(name = "admin_auth_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSettings {

    @Id
    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "providers_enabled", length = 256)
    private String providersEnabled;  // comma-separated: PASSWORD,OTP,OIDC

    @Embedded
    private OtpPolicy otpPolicy;

    @Embedded
    private OidcConfig oidc;

    @Embedded
    private SessionPolicy sessionPolicy;

    @Column(name = "risk_rules_json", columnDefinition = "TEXT")
    private String riskRulesJson;     // serialized List<RiskRule>

    @Column(name = "allowed_origins", length = 1024)
    private String allowedOriginsCsv;  // comma-separated

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    // -------------------------------------------------------------------
    // Convenience: get / set for risk rules as List<RiskRule>
    // -------------------------------------------------------------------

    public List<RiskRule> getRiskRules() {
        if (riskRulesJson == null || riskRulesJson.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(riskRulesJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setRiskRules(List<RiskRule> rules) {
        try {
            this.riskRulesJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rules);
        } catch (Exception ignored) {
            this.riskRulesJson = "[]";
        }
    }

    public List<String> getAllowedOrigins() {
        if (allowedOriginsCsv == null || allowedOriginsCsv.isBlank()) return List.of();
        return List.of(allowedOriginsCsv.split(","));
    }

    public void setAllowedOrigins(List<String> origins) {
        this.allowedOriginsCsv = origins == null ? "" : String.join(",", origins);
    }

    // -------------------------------------------------------------------
    // Value objects
    // -------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OtpPolicy {
        private int codeLength;             // 6 or 8
        private String algorithm;           // SHA1, SHA256, SHA512
        private int expirySeconds;          // 15..600
        private int maxAttempts;            // 3..10
        private int lockoutMinutes;         // 5..60
        private int backupCodesPerUser;     // 0..20
        private boolean allowQrScan;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OidcConfig {
        private String providerType;        // OIDC, SAML, DISABLED
        private String discoveryUrl;
        private String clientId;
        /** Stored encrypted-at-rest; never returned in GET responses */
        private String clientSecret;
        private String scopes;              // space-separated
        private String signInButtonLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionPolicy {
        private int accessTokenMinutes;     // 5..1440
        private int refreshTokenDays;       // 1..90
        private int maxConcurrentSessions;  // 1..20
        private String deviceFingerprintPolicy;  // WARN, BLOCK, PERMISSIVE
        private boolean sameDeviceReauth;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RiskRule {
        private String rule;          // e.g. "new_device", "unusual_location"
        private String description;
        private boolean enabled;
        private String action;        // BLOCK, WARN, ALLOW, BLOCK_AND_NOTIFY
    }
}
