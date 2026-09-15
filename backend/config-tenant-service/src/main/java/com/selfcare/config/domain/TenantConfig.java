package com.selfcare.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tenant master configuration.
 *
 * Created via selfcare Studio or API. Contains:
 * - Tenant metadata
 * - Industry/LOB configuration
 * - Supported locales
 * - Feature entitlements
 * - Provider binding references
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenant_configs")
public class TenantConfig {

    @Id
    private String id;

    /** Unique tenant identifier, e.g., "dialog-lk" */
    @Indexed(unique = true)
    private String tenantId;

    private String name;           // "Dialog"
    private String operator;       // "dialog"
    private String country;        // "LK"

    /** Industry: TELECOM, INSURANCE, TRAVEL */
    private String industry;

    /** Supported lines of business */
    private List<String> supportedLobs;

    /** Supported locales */
    private List<String> supportedLocales;
    private String defaultLocale;

    /** Environments: dev, qa, staging, prod */
    private Map<String, EnvironmentConfig> environments;

    /** Active features for this tenant */
    private List<String> enabledFeatures;

    /** Operator pack version */
    private String packVersion;

    /** Provider adapter bindings */
    private Map<String, String> providerBindings;

    /** Status: ACTIVE, SUSPENDED, INACTIVE */
    @Indexed
    private String status;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentConfig {
        private String apiBaseUrl;
        private String wsBaseUrl;
        private Map<String, String> secrets; // References only, not values
        private boolean active;
    }
}