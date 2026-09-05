package com.omobio.platform.common.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Tenant configuration properties loaded from application configuration.
 *
 * Each tenant registered in the platform has properties here that control
 * behavior: which operator pack, adapter bindings, feature overrides, etc.
 *
 * Example application.properties:
 *   omobio.tenants.dialog-lk.operator=dialog
 *   omobio.tenants.dialog-lk.name=Dialog
 *   omobio.tenants.dialog-lk.country=LK
 *   omobio.tenants.dialog-lk.adapterPackage=com.omobio.providers.dialog
 *   omobio.tenants.hutch-lk.operator=hutch
 *   omobio.tenants.hutch-lk.name=Hutch
 *   omobio.tenants.hutch-lk.country=LK
 *   omobio.tenants.hutch-lk.adapterPackage=com.omobio.providers.hutch
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "omobio.tenants")
public class TenantProperties {

    private Map<String, TenantConfig> tenants = Map.of();

    @Data
    public static class TenantConfig {
        @NotBlank
        private String operator;

        @NotBlank
        private String name;

        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}$")
        private String country;

        @NotBlank
        private String adapterPackage;

        /** Environment: dev, qa, staging, prod */
        private String defaultEnvironment = "dev";

        /** Which operator pack version to use */
        private String packVersion;

        /** Which theme to apply by default */
        private String defaultTheme;

        /** Supported locales for this tenant */
        private List<String> supportedLocales = List.of("en");

        /** Default locale */
        private String defaultLocale = "en";

        /** Supported LOBs: MOBILE, BROADBAND, DTV, FIBRE, etc. */
        private List<String> supportedLobs = List.of("MOBILE");

        /** Whether this tenant is active */
        private boolean active = true;

        /** Custom properties map for tenant-specific overrides */
        private Map<String, String> custom;
    }

    /**
     * Resolve the tenant config for a given tenant ID.
     * Returns null if the tenant is not registered.
     */
    public TenantConfig getTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenants.get(tenantId.toLowerCase());
    }

    /**
     * Check if a tenant is registered and active.
     */
    public boolean isTenantActive(String tenantId) {
        TenantConfig cfg = getTenant(tenantId);
        return cfg != null && cfg.isActive();
    }

    /**
     * Get the adapter package for a tenant.
     */
    public String getAdapterPackage(String tenantId) {
        TenantConfig cfg = getTenant(tenantId);
        return cfg != null ? cfg.getAdapterPackage() : null;
    }
}
