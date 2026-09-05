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
 * Balance widget — shows the current balance for the active connection.
 *
 * Calls usage-service for real-time balance.
 * Returns: { amount, currency, expiryDate, type }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceWidget implements WidgetProvider {

    private final DashboardClient dashboardClient;

    @Override
    public String getWidgetId() {
        return "balance";
    }

    @Override
    public String getDisplayName() {
        return "Balance";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        return connectionId != null && !connectionId.isBlank();
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        return dashboardClient.getBalance(connectionId)
                .map(response -> (Object) Map.of(
                        "amount", extractAmount(response),
                        "currency", extractCurrency(response, "LKR"),
                        "expiryDate", extractExpiryDate(response),
                        "type", extractType(response, "POSTPAID")
                ))
                .onErrorResume(e -> {
                    log.warn("Balance widget failed for connection {}: {}", connectionId, e.getMessage());
                    return Mono.just(Map.of(
                            "amount", BigDecimal.ZERO,
                            "currency", "LKR",
                            "expiryDate", null,
                            "type", "UNKNOWN",
                            "_error", e.getMessage()
                    ));
                });
    }

    @Override
    public DataFreshness getFreshness() {
        return DataFreshness.REAL_TIME;
    }

    // -------------------------------------------------------------------------
    // Response field extraction helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private BigDecimal extractAmount(Object response) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            Object amount = map.get("amount");
            if (amount instanceof Number) return BigDecimal.valueOf(((Number) amount).doubleValue());
            if (amount instanceof String) return new BigDecimal((String) amount);
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private String extractCurrency(Object response, String fallback) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            return (String) map.getOrDefault("currency", fallback);
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private LocalDate extractExpiryDate(Object response) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            Object expiry = map.get("expiryDate");
            if (expiry instanceof String) {
                return LocalDate.parse((String) expiry);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractType(Object response, String fallback) {
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            return (String) map.getOrDefault("type", fallback);
        }
        return fallback;
    }
}
