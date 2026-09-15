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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the package-details widget (midend getBasePackageDetail analogue)
 * delegates to product-service active-packages via DashboardClient.
 */
@ExtendWith(MockitoExtension.class)
class PackageDetailWidgetTest {

    @Mock private DashboardClient dashboardClient;

    private PackageDetailWidget widget;

    @BeforeEach
    void setUp() {
        widget = new PackageDetailWidget(dashboardClient);
    }

    @Test
    @DisplayName("Returns active packages from product-service")
    void returnsActivePackages() {
        Object payload = List.of(Map.of(
                "packageId", "pkg-1",
                "packageName", "Postpaid MyPlan 899",
                "status", "ACTIVE"));

        when(dashboardClient.getActivePackages("0771234567")).thenReturn(Mono.just(payload));

        Object result = widget.execute("0771234567", "dialog-lk", "POSTPAID").block();

        assertThat(result).isEqualTo(payload);
        verify(dashboardClient).getActivePackages("0771234567");
    }

    @Test
    @DisplayName("Returns empty list when no connection is provided")
    void returnsEmptyWithoutConnection() {
        Object result = widget.execute("", "dialog-lk", "POSTPAID").block();

        assertThat(result).isEqualTo(List.of());
        verify(dashboardClient, never()).getActivePackages(eq(""));
    }

    @Test
    @DisplayName("Returns fallback marker on upstream error")
    void returnsFallbackOnError() {
        when(dashboardClient.getActivePackages("0771234567"))
                .thenReturn(Mono.error(new RuntimeException("bss down")));

        Object result = widget.execute("0771234567", "dialog-lk", "POSTPAID").block();

        assertThat(result).isInstanceOf(List.class);
    }
}