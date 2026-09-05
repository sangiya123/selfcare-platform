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
 * Banners widget — shows active banners from the content service.
 *
 * Calls content-service for banners configured for this tenant + channel.
 * Returns: list of { id, title, imageUrl, action, priority }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BannersWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "banners";
    }

    @Override
    public String getDisplayName() {
        return "Banners";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        // Always available
        return true;
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        return dashboardClient.getActiveBanners(tenantId, "APP")
                .map(this::parseBanners)
                .onErrorResume(e -> {
                    log.warn("Banners widget failed: tenant={}, error={}", tenantId, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    @Override
    public DataFreshness getFreshness() {
        return DataFreshness.NEAR_REAL_TIME;
    }

    @Override
    public Long getTimeoutMs() {
        return 300L;
    }

    @SuppressWarnings("unchecked")
    private Object parseBanners(Object response) {
        if (response instanceof List) {
            return response;
        }
        return List.of();
    }
}
