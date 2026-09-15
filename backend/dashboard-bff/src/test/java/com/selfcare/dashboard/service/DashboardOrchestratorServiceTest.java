package com.selfcare.dashboard.service;

import com.selfcare.dashboard.web.dto.DashboardResponse;
import com.selfcare.dashboard.web.dto.WidgetResult;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies the dashboard orchestrator partial-response contract (ADR-008):
 * - One slow widget must NOT block other widgets
 * - Deadline truncates results
 * - Overall status reflects partial vs full completion
 */
class DashboardOrchestratorServiceTest {

    private DashboardOrchestratorService service;
    private MockWidgetProvider balanceWidget;
    private MockWidgetProvider billWidget;

    @BeforeEach
    void setUp() {
        balanceWidget = new MockWidgetProvider("balance", true);
        billWidget = new MockWidgetProvider("bills", true);

        Map<String, WidgetProvider> providers = Map.of(
                "balance", balanceWidget,
                "bills", billWidget);

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.ofDefaults();

        DashboardLayoutService layoutService = new DashboardLayoutService(
                org.mockito.Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class),
                org.mockito.Mockito.mock(org.springframework.data.mongodb.core.MongoTemplate.class));

        service = new DashboardOrchestratorService(
                providers, layoutService, cbRegistry, tlRegistry,
                500,   // overallDeadlineMs
                200);  // defaultWidgetTimeoutMs

        // Set correlation ID via TenantContext
        com.selfcare.platform.common.tenant.TenantContext.current().setCorrelationId("corr-123");
    }

    // --- orchestrateDashboard ---

    @Test
    @DisplayName("Returns SUCCESS when all widgets succeed within deadline")
    void orchestrateDashboard_allSucceed() {
        DashboardResponse r = service.orchestrateDashboard(
                "t1", "conn-A", "default", List.of("balance", "bills"))
                .block(Duration.ofSeconds(3));

        assertThat(r).isNotNull();
        assertThat(r.getOverallStatus()).isEqualTo("SUCCESS");
        assertThat(r.getWidgets()).containsOnlyKeys("balance", "bills");
        assertThat(r.getWidgets().get("balance").getStatus()).isEqualTo("SUCCESS");
        assertThat(r.getWidgets().get("bills").getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Returns PARTIAL when some widgets timeout")
    void orchestrateDashboard_partialOnTimeout() {
        billWidget.setDelay(Duration.ofMillis(300)); // exceeds widget timeout of 200ms

        DashboardResponse r = service.orchestrateDashboard(
                "t1", "conn-A", "default", List.of("balance", "bills"))
                .block(Duration.ofSeconds(3));

        assertThat(r).isNotNull();
        assertThat(r.getOverallStatus()).isEqualTo("PARTIAL");
        assertThat(r.getWidgets().get("balance").getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Returns ERROR status when all widgets fail")
    void orchestrateDashboard_allError() {
        balanceWidget.setError(new RuntimeException("Provider down"));
        billWidget.setError(new RuntimeException("Provider down"));

        DashboardResponse r = service.orchestrateDashboard(
                "t1", "conn-A", "default", List.of("balance", "bills"))
                .block(Duration.ofSeconds(3));

        assertThat(r).isNotNull();
        assertThat(r.getOverallStatus()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Returns UNAVAILABLE for unknown widget IDs")
    void orchestrateDashboard_unknownWidget() {
        DashboardResponse r = service.orchestrateDashboard(
                "t1", "conn-A", "default", List.of("balance", "unknown-widget"))
                .block(Duration.ofSeconds(3));

        assertThat(r.getWidgets()).containsKey("balance");
        assertThat(r.getWidgets()).containsKey("unknown-widget");
        assertThat(r.getWidgets().get("unknown-widget").getStatus()).isEqualTo("UNAVAILABLE");
    }

    @Test
    @DisplayName("Includes correlation ID and elapsed time in response")
    void orchestrateDashboard_metadata() {
        DashboardResponse r = service.orchestrateDashboard(
                "t1", "conn-A", "default", List.of("balance"))
                .block(Duration.ofSeconds(3));

        assertThat(r.getCorrelationId()).isEqualTo("corr-123");
        assertThat(r.getElapsedMs()).isNotNull();
        assertThat(r.getTimestamp()).isNotNull();
    }

    // --- refreshWidget ---

    @Test
    @DisplayName("refreshWidget returns UNAVAILABLE for unknown widget")
    void refreshWidget_unknownWidget() {
        WidgetResult r = service.refreshWidget("unknown", "t1", "conn-A", "default")
                .block(Duration.ofSeconds(1));
        assertThat(r.getStatus()).isEqualTo("UNAVAILABLE");
    }

    @Test
    @DisplayName("refreshWidget returns SUCCESS for known widget")
    void refreshWidget_success() {
        balanceWidget.setDelay(Duration.ofMillis(50));
        WidgetResult r = service.refreshWidget("balance", "t1", "conn-A", "default")
                .block(Duration.ofSeconds(1));
        assertThat(r.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("refreshWidget returns TIMEOUT when widget is slow")
    void refreshWidget_timeout() {
        balanceWidget.setDelay(Duration.ofMillis(500)); // > 200ms widget timeout
        WidgetResult r = service.refreshWidget("balance", "t1", "conn-A", "default")
                .block(Duration.ofSeconds(2));
        assertThat(r.getStatus()).isEqualTo("TIMEOUT");
    }

    // --- determineOverallStatus (via orchestrateDashboard) ---

    @Test
    @DisplayName("Single widget with error → overall ERROR")
    void overallStatus_singleError() {
        balanceWidget.setError(new RuntimeException("err"));
        DashboardResponse r = service.orchestrateDashboard(
                "t1", "conn-A", "default", List.of("balance"))
                .block(Duration.ofSeconds(3));
        assertThat(r.getOverallStatus()).isEqualTo("ERROR");
    }

    // -------------------------------------------------------------------------
    // Mock widget provider
    // -------------------------------------------------------------------------
    private static class MockWidgetProvider implements WidgetProvider {
        private final String widgetId;
        private Duration delay = Duration.ofMillis(0);
        private Exception error = null;
        private boolean available = true;

        MockWidgetProvider(String widgetId, boolean available) {
            this.widgetId = widgetId;
            this.available = available;
        }

        void setDelay(Duration delay) { this.delay = delay; }
        void setError(Exception e) { this.error = e; }

        @Override
        public String getWidgetId() {
            return widgetId;
        }

        @Override
        public String getDisplayName() {
            return widgetId;
        }

        @Override
        public Mono<Object> execute(String connectionId, String tenantId, String profileKey) {
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.delay(delay).thenReturn(widgetId + "-data");
        }

        @Override
        public boolean isAvailable(String tenantId, String connectionId, String profileKey) {
            return available;
        }
    }
}
