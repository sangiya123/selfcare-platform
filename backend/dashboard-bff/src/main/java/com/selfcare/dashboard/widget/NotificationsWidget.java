package com.selfcare.dashboard.widget;

import com.selfcare.dashboard.service.DashboardClient;
import com.selfcare.dashboard.service.WidgetProvider;
import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Notifications widget — shows recent notifications for the authenticated user.
 *
 * Calls notification-service for the user's recent notifications.
 * Returns: list of { id, title, message, type, read, timestamp }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationsWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "notifications";
    }

    @Override
    public String getDisplayName() {
        return "Notifications";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        // Available if we have a user ID
        return TenantContext.get().getUserId() != null;
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        String userId = TenantContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            return Mono.just(List.of());
        }

        return dashboardClient.getRecentNotifications(userId, 5)
                .map(this::parseNotifications)
                .onErrorResume(e -> {
                    log.warn("Notifications widget failed for user {}: {}", userId, e.getMessage());
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
    private Object parseNotifications(Object response) {
        if (response instanceof List) {
            return response;
        }
        return List.of();
    }
}
