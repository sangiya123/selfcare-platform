package com.omobio.dashboard.widget;

import com.omobio.dashboard.service.DashboardClient;
import com.omobio.dashboard.service.WidgetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Bill widget — shows the current bill for postpaid connections.
 *
 * Calls billing-service for the current bill.
 * Returns: { amount, dueDate, status }
 *
 * Availability: only for postpaid connection types.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "bill";
    }

    @Override
    public String getDisplayName() {
        return "Current Bill";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        // Only show for postpaid profiles
        return "POSTPAID".equals(profileKey) || "PREMIUM".equals(profileKey);
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        return dashboardClient.getCurrentBill(connectionId)
                .map(response -> (Object) Map.of(
                        "amount", extractAmount(response),
                        "dueDate", extractDueDate(response),
                        "status", extractStatus(response, "PENDING")
                ))
                .onErrorResume(e -> {
                    log.warn("Bill widget failed for connection {}: {}", connectionId, e.getMessage());
                    return Mono.just(Map.of(
                            "amount", BigDecimal.ZERO,
                            "dueDate", null,
                            "status", "UNKNOWN",
                            "_error", e.getMessage()
                    ));
                });
    }

    @Override
    public DataFreshness getFreshness() {
        return DataFreshness.NEAR_REAL_TIME;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractAmount(Object response) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            Object amount = map.get("amountDue") != null ? map.get("amountDue") : map.get("amount");
            if (amount instanceof Number) return BigDecimal.valueOf(((Number) amount).doubleValue());
            if (amount instanceof String) return new BigDecimal((String) amount);
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private LocalDate extractDueDate(Object response) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            Object dueDate = map.get("dueDate");
            if (dueDate instanceof String) {
                try { return LocalDate.parse((String) dueDate); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractStatus(Object response, String fallback) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            return (String) map.getOrDefault("status", fallback);
        }
        return fallback;
    }
}
