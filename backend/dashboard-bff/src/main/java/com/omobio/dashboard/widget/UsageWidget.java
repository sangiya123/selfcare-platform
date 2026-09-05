package com.omobio.dashboard.widget;

import com.omobio.dashboard.service.DashboardClient;
import com.omobio.dashboard.service.WidgetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Usage widget — shows usage summary (data, voice, SMS) for the active connection.
 *
 * Calls usage-service for usage breakdown.
 * Returns: { data: { used, total, unit }, voice: { used, total, unit }, sms: { used, total, unit } }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "usage";
    }

    @Override
    public String getDisplayName() {
        return "Usage Summary";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        return connectionId != null && !connectionId.isBlank();
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        return dashboardClient.getUsage(connectionId)
                .map(this::parseUsageResponse)
                .onErrorResume(e -> {
                    log.warn("Usage widget failed for connection {}: {}", connectionId, e.getMessage());
                    return Mono.just(Map.of(
                            "data", Map.of("used", 0, "total", 0, "unit", "GB", "_error", e.getMessage()),
                            "voice", Map.of("used", 0, "total", 0, "unit", "min", "_error", e.getMessage()),
                            "sms", Map.of("used", 0, "total", 0, "unit", "SMS", "_error", e.getMessage())
                    ));
                });
    }

    @Override
    public DataFreshness getFreshness() {
        return DataFreshness.REAL_TIME;
    }

    @SuppressWarnings("unchecked")
    private Object parseUsageResponse(Object response) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            return Map.of(
                    "data", parseResource(map.get("data")),
                    "voice", parseResource(map.get("voice")),
                    "sms", parseResource(map.get("sms"))
            );
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResource(Object resource) {
        if (resource instanceof Map) {
            return (Map<String, Object>) resource;
        }
        return Map.of("used", 0, "total", 0, "unit", "unknown");
    }
}
