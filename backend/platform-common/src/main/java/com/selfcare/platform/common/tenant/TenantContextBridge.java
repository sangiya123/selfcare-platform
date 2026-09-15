package com.selfcare.platform.common.tenant;

/**
 * Bridge helper to access the current tenant from outside the request
 * scope (e.g., from adapter calls that don't receive the tenantId
 * explicitly through their interface).
 */
public final class TenantContextBridge {

    private TenantContextBridge() {}

    /**
     * Get the current tenant ID from the thread-local context.
     * Returns the configured default tenant if no context is set
     * (fallback only — should not happen in normal request flow).
     */
    public static String currentTenantId() {
        try {
            String id = TenantContext.get().getTenantId();
            if (id != null && !id.isEmpty()) return id;
        } catch (Exception ignored) {}
        // Fallback to default — only used outside request scope
        return "default";
    }
}
