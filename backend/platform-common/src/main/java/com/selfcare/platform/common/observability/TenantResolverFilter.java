package com.selfcare.platform.common.observability;

// Re-export for use by CorrelationIdFilter — same constants
public final class TenantResolverFilter {
    public static final String TENANT_HEADER = "X-Tenant-Id";
    private TenantResolverFilter() {}
}
