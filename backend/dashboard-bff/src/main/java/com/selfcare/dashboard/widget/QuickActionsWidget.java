package com.selfcare.dashboard.widget;

import com.selfcare.dashboard.service.WidgetProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Quick Actions widget — static list of shortcut actions available on the dashboard.
 *
 * Returns a fixed list of action shortcuts. Actions are always available.
 * Returns: list of { id, label, icon, action, category }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuickActionsWidget implements WidgetProvider {

    @Override
    public String getWidgetId() {
        return "quick-actions";
    }

    @Override
    public String getDisplayName() {
        return "Quick Actions";
    }

    @Override
    public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
        // Always available
        return true;
    }

    @Override
    public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
        return Mono.just(List.of(
                Map.of(
                        "id", "recharge",
                        "label", "Recharge",
                        "icon", "topup",
                        "action", "/recharge",
                        "category", "payment"
                ),
                Map.of(
                        "id", "pay-bill",
                        "label", "Pay Bill",
                        "icon", "receipt",
                        "action", "/pay-bill",
                        "category", "payment"
                ),
                Map.of(
                        "id", "buy-bundle",
                        "label", "Buy Bundle",
                        "icon", "add-ons",
                        "action", "/bundles",
                        "category", "plans"
                ),
                Map.of(
                        "id", "check-usage",
                        "label", "Check Usage",
                        "icon", "chart",
                        "action", "/usage",
                        "category", "info"
                ),
                Map.of(
                        "id", "support",
                        "label", "Get Help",
                        "icon", "help",
                        "action", "/support",
                        "category", "support"
                ),
                Map.of(
                        "id", "refer-friend",
                        "label", "Refer a Friend",
                        "icon", "share",
                        "action", "/refer",
                        "category", "loyalty"
                )
        ));
    }

    @Override
    public DataFreshness getFreshness() {
        return DataFreshness.REFERENCE;
    }

    @Override
    public Long getTimeoutMs() {
        // Static widget — should be fast
        return 100L;
    }
}
