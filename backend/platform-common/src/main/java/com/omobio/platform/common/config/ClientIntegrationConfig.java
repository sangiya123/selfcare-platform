package com.omobio.platform.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Per-tenant client integration configuration.
 *
 * Stored in MongoDB `client_integrations` collection.
 * One document per (tenantId, integrationType).
 *
 * The config is loaded by {@link com.omobio.platform.common.tenant.TenantConfigurationService}
 * and consumed by the industry-pack provider beans (telco operators, insurance
 * companies, travel companies, etc.).
 *
 * This replaces the previous approach of putting operator URLs and
 * credentials in env files. Now the admin portal "Integrations" page
 * is the single source of truth.
 *
 * Terminology:
 * - "tenant" / "client" = the business using the OMOBIO platform
 *   (Dialog, Hutch, Airtel, AIA, etc.)
 * - "industry" = the vertical (TELCO, INSURANCE, TRAVEL, ...)
 * - "industry pack" = the per-vertical provider implementation
 *   (telco providers, insurance providers, ...)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "client_integrations")
@CompoundIndex(name = "ix_tenant_type", def = "{ 'tenantId': 1, 'integrationType': 1 }", unique = true)
public class ClientIntegrationConfig {

    @Id
    private String id;

    /** Tenant this config belongs to, e.g. "dialog-lk", "aia-lk" */
    @Indexed
    private String tenantId;

    /** Industry this integration belongs to: TELCO, INSURANCE, TRAVEL, ... */
    @Indexed
    private String industry;

    /** Integration type, e.g. (TELCO):
     *  - DIALOG_MIFE        (Dialog auth/oauth)
     *  - DIALOG_BSS         (Dialog balance/usage)
     *  - DIALOG_SMSC        (Dialog SMS gateway)
     *  - DIALOG_CATALOG     (Dialog product catalog)
     *  - HUTCH_BSS          (Hutch balance/usage)
     *  - HUTCH_SMSC         (Hutch SMS gateway)
     *  - AIRTEL_GATEWAY     (Airtel payment gateway)
     *  - STRIPE             (Stripe international — any industry)
     *  - FIREBASE_PUSH      (Firebase Cloud Messaging)
     *  - TWILIO_SMS         (Twilio)
     *  - SENDGRID_EMAIL     (SendGrid)
     *  - CUSTOM             (any other)
     *
     *  Insurance:
     *  - AIA_INSURANCE      (AIA policy/claims/beneficiaries/premiums)
     */
    private String integrationType;

    /** Provider class to load, e.g. "com.omobio.dialog.provider.DialogAuthProvider" */
    private String providerClass;

    /** Base URL of the upstream API */
    private String baseUrl;

    /** Authentication type: NONE, API_KEY, BASIC, OAUTH2_CLIENT_CREDENTIALS, MTLS, JWT */
    private String authType;

    /** Auth credentials (API key, OAuth client ID/secret, etc.) — encrypted at rest */
    private Map<String, String> credentials;

    /** Field mapping: canonical → client/operator field path */
    private Map<String, String> fieldMapping;

    /** Timeouts, retry policy, circuit breaker thresholds */
    private Map<String, String> advanced;

    /** Status: ACTIVE, DRAFT, DISABLED */
    private String status;

    /** Last health check result */
    private HealthStatus health;

    /** Free-form metadata (headers, custom params, country-specific config) */
    private Map<String, String> metadata;

    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthStatus {
        private String status; // HEALTHY, UNHEALTHY, UNKNOWN
        private Instant lastChecked;
        private Long responseTimeMs;
        private String lastError;
    }

    /**
     * Get a credential value (returns null if not set).
     */
    public String getCredential(String key) {
        return credentials != null ? credentials.get(key) : null;
    }
}
