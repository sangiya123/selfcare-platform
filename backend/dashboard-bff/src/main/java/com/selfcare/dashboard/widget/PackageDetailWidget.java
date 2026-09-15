package com.selfcare.dashboard.widget;

import com.selfcare.dashboard.service.DashboardClient;
import com.selfcare.dashboard.service.WidgetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Package details widget — shows the connection's active base package / add-ons.
 *
 * <p>Mirrors the legacy midend {@code getBasePackageDetail} dashboard
 * component. Data comes from product-service which resolves active packages
 * through the operator's {@code ActivationProvider} (BSS call), keeping the
 * external data flow unchanged.</p>
 *
 * Returns: list of active packages { packageId, productCode, packageName,
 * activatedAt, expiresAt, status, autoRenew }.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PackageDetailWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "package-details";
    }

    @Override
    public String getDisplayName() {
        return "Base Package";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        return true;
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        if (connectionId == null || connectionId.isBlank()) {
            return Mono.just(List.of());
        }

        return dashboardClient.getActivePackages(connectionId)
                .map(this::parsePackages)
                .onErrorResume(e -> {
                    log.warn("Package details widget failed: tenant={}, connection={}, error={}",
                            tenantId, connectionId, e.getMessage());
                    return Mono.just(List.of(Map.of("_error", e.getMessage())));
                });
    }

    @Override
    public Long getTimeoutMs() {
        return 400L;
    }

    private Object parsePackages(Object response) {
        if (response instanceof List) {
            return response;
        }
        return List.of();
    }
}