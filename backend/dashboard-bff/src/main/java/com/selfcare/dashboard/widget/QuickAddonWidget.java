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
 * Quick add-on and reload widget — quick data add-ons (and reload amounts for
 * prepaid profiles) on the home dashboard.
 *
 * <p>Mirrors the legacy midend {@code getQuickAddonAndReload} dashboard
 * component. Data comes from product-service's package browse endpoint, which
 * returns the materialized catalog enriched with the caller's eligibility and
 * subscription state — so the same packages the app already shows are
 * reproduced without changing the catalog/BSS data flows.</p>
 *
 * Returns: list of package offers { productId, productCode, name, amount,
 * price, validityDays, eligible, active }.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuickAddonWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "quick-addon-and-reload";
    }

    @Override
    public String getDisplayName() {
        return "Quick Add-on & Reload";
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

        String lob = "DATA";
        return dashboardClient.getPackagesByLob(lob, connectionId)
                .map(this::parseAddons)
                .onErrorResume(e -> {
                    log.warn("Quick add-on widget failed: tenant={}, connection={}, error={}",
                            tenantId, connectionId, e.getMessage());
                    return Mono.just(List.of(Map.of("_error", e.getMessage())));
                });
    }

    @Override
    public Long getTimeoutMs() {
        return 400L;
    }

    private Object parseAddons(Object response) {
        if (response instanceof List) {
            return response;
        }
        return List.of();
    }
}