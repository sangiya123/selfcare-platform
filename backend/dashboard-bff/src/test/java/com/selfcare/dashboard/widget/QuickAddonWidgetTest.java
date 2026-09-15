package com.selfcare.dashboard.widget;

import com.selfcare.dashboard.service.DashboardClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the quick add-on and reload widget (midend getQuickAddonAndReload
 * analogue) fetches DATA add-ons via product-service package browse.
 */
@ExtendWith(MockitoExtension.class)
class QuickAddonWidgetTest {

    @Mock private DashboardClient dashboardClient;

    private QuickAddonWidget widget;

    @BeforeEach
    void setUp() {
        widget = new QuickAddonWidget(dashboardClient);
    }

    @Test
    @DisplayName("Returns DATA add-ons for the connection")
    void returnsDataAddons() {
        Object payload = List.of(Map.of(
                "productId", "1GB-1D",
                "productCode", "1MORE1D",
                "name", "1 GB Anytime",
                "price", 60,
                "eligible", true));

        when(dashboardClient.getPackagesByLob("DATA", "0771234567")).thenReturn(Mono.just(payload));

        Object result = widget.execute("0771234567", "dialog-lk", "PREPAID").block();

        assertThat(result).isEqualTo(payload);
        verify(dashboardClient).getPackagesByLob("DATA", "0771234567");
    }

    @Test
    @DisplayName("Returns empty list when no connection is provided")
    void returnsEmptyWithoutConnection() {
        Object result = widget.execute("", "dialog-lk", "PREPAID").block();

        assertThat(result).isEqualTo(List.of());
        verify(dashboardClient, never()).getPackagesByLob("DATA", "");
    }

    @Test
    @DisplayName("Returns fallback marker on upstream error")
    void returnsFallbackOnError() {
        when(dashboardClient.getPackagesByLob("DATA", "0771234567"))
                .thenReturn(Mono.error(new RuntimeException("catalog down")));

        Object result = widget.execute("0771234567", "dialog-lk", "PREPAID").block();

        assertThat(result).isInstanceOf(List.class);
    }
}