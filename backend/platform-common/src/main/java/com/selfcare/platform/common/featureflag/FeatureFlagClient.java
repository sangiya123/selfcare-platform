package com.selfcare.platform.common.featureflag;

import java.util.Map;

/**
 * Feature flag client interface.
 *
 * Evaluates feature flags for a tenant, with optional context attributes.
 * Implementations include UnleashFeatureFlagClient (production) and
 * a NoOpFeatureFlagClient (default when Unleash is not configured).
 *
 * All flag evaluations are tenant-aware by default.
 */
public interface FeatureFlagClient {

    /**
     * Check if a feature flag is enabled for a tenant.
     *
     * @param flagName  The flag name (e.g., "usage.chart.enabled")
     * @param tenantId  The tenant identifier
     * @return FeatureFlagResult with enabled/disabled/error state
     */
    FeatureFlagResult isEnabled(String flagName, String tenantId);

    /**
     * Get the variant for a feature flag for a tenant.
     *
     * @param flagName  The flag name
     * @param tenantId  The tenant identifier
     * @return FeatureFlagResult with variant value
     */
    FeatureFlagResult getVariant(String flagName, String tenantId);

    /**
     * Check if a feature flag is enabled with a single context attribute.
     */
    FeatureFlagResult isEnabled(String flagName, String tenantId, String key, Object value);

    /**
     * Get variant with a single context attribute.
     */
    FeatureFlagResult getVariant(String flagName, String tenantId, String key, Object value);

    /**
     * Check if a feature flag is enabled with multiple context attributes.
     */
    FeatureFlagResult isEnabled(String flagName, String tenantId, Map<String, Object> contextAttributes);

    /**
     * Get variant with multiple context attributes.
     */
    FeatureFlagResult getVariant(String flagName, String tenantId, Map<String, Object> contextAttributes);

    /**
     * Convenience: evaluate flag for the current request's tenant.
     */
    default boolean isEnabled(String flagName) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        return isEnabled(flagName, tenantId).isEnabled();
    }

    /**
     * Convenience: get variant for the current request's tenant.
     */
    default String getVariant(String flagName) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        return getVariant(flagName, tenantId).getVariant();
    }
}