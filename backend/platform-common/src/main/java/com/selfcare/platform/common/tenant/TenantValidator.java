package com.selfcare.platform.common.tenant;

public interface TenantValidator {

    /**
     * Check if a tenant is registered and active.
     * @param tenantId the tenant identifier
     * @return true if tenant exists and is active, false otherwise
     */
    boolean isTenantActive(String tenantId);

    /**
     * Get the adapter package for a tenant.
     * @param tenantId the tenant identifier
     * @return adapter package or null if not found
     */
    String getAdapterPackage(String tenantId);
}