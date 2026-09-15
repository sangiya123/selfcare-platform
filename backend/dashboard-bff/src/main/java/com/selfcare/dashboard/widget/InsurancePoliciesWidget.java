package com.selfcare.dashboard.widget;

import com.selfcare.dashboard.service.DashboardClient;
import com.selfcare.dashboard.service.WidgetProvider;
import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Insurance Policies widget — shows insurance policies for insurance-industry tenants.
 *
 * Available ONLY for insurance tenants (isAvailable checks tenant industry).
 * Calls insurance-service for the user's active policies.
 * Returns: list of { policyId, name, status, premiumAmount, nextDueDate }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsurancePoliciesWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Value("${selfcare.tenant.industry:TELCO}")
    private String defaultIndustry;

    @Override
    public String getWidgetId() {
        return "insurance-policies";
    }

    @Override
    public String getDisplayName() {
        return "My Policies";
    }

    /**
     * Only available for INSURANCE-industry tenants.
     */
    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        // In production: read from TenantConfigurationService
        // For now, check a config property
        String industry = resolveIndustry(tenantId);
        return "INSURANCE".equals(industry);
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        String userId = TenantContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            return Mono.just(List.of());
        }

        return dashboardClient.getInsurancePolicies(userId)
                .map(this::parsePolicies)
                .onErrorResume(e -> {
                    log.warn("Insurance policies widget failed for user {}: {}", userId, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    @Override
    public DataFreshness getFreshness() {
        return DataFreshness.NEAR_REAL_TIME;
    }

    @Override
    public Long getTimeoutMs() {
        return 400L;
    }

    @SuppressWarnings("unchecked")
    private Object parsePolicies(Object response) {
        if (response instanceof List) {
            return response;
        }
        return List.of();
    }

    /**
     * Resolve industry from tenant config.
     * In production: call TenantConfigurationService.
     */
    private String resolveIndustry(String tenantId) {
        // Stub: read from TenantContext or config
        // In production: return tenantConfigurationService.getIndustry(tenantId);
        return defaultIndustry;
    }
}
