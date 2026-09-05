package com.omobio.dashboard.widget;

import com.omobio.dashboard.service.DashboardClient;
import com.omobio.dashboard.service.WidgetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Bundles widget — shows featured bundles/packages for the active connection.
 *
 * Calls product-service for featured bundles matching the user's LOB.
 * Returns: list of { productId, name, description, price, currency, urgency }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BundlesWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "bundles";
    }

    @Override
    public String getDisplayName() {
        return "Featured Bundles";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        // Available for all tenants
        return true;
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        // Determine LOB from profileKey (or default to MOBILE)
        String lob = profileKey != null && !profileKey.isBlank() ? "MOBILE" : "MOBILE";

        return dashboardClient.getFeaturedBundles(tenantId, lob)
                .map(this::parseBundles)
                .onErrorResume(e -> {
                    log.warn("Bundles widget failed: tenant={}, error={}", tenantId, e.getMessage());
                    return Mono.just(List.of(
                            Map.of(
                                    "productId", "default-data-pack",
                                    "name", "1GB Daily Data",
                                    "description", "Perfect for daily social media browsing",
                                    "price", 49.0,
                                    "currency", "LKR",
                                    "urgency", "normal",
                                    "_error", e.getMessage()
                            )
                    ));
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
    private Object parseBundles(Object response) {
        if (response instanceof List) {
            return response;
        }
        return List.of();
    }
}
