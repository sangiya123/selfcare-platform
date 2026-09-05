package com.omobio.ai.gateway.service;

import com.omobio.ai.service.OpsAiCopilotService;
import com.omobio.ai.service.OpsAiCopilotService.Alert;
import com.omobio.ai.service.OpsAiCopilotService.CapacityForecast;
import com.omobio.ai.service.OpsAiCopilotService.DeduplicatedAlert;
import com.omobio.ai.service.OpsAiCopilotService.IncidentInput;
import com.omobio.ai.service.OpsAiCopilotService.IncidentSummary;
import com.omobio.ai.service.OpsAiCopilotService.LogEntry;
import com.omobio.ai.service.OpsAiCopilotService.Metric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OpsAiCopilotServiceTest {

    private OpsAiCopilotService service;

    @BeforeEach
    void setUp() {
        service = new OpsAiCopilotService(null);
    }

    @Test
    @DisplayName("summarizeIncident returns UNKNOWN for empty input")
    void summarize_empty() {
        IncidentSummary s = service.summarizeIncident(new IncidentInput(List.of(), List.of(), List.of(), null, null));
        assertThat(s.severity()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("summarizeIncident assigns P1 for high error spike with many 5xx")
    void summarize_p1() {
        IncidentInput input = new IncidentInput(
                List.of(new LogEntry("ERROR", "Connection refused", Instant.now())),
                List.of(new Metric("5xx_rate", 25.0, Instant.now()),
                        new Metric("5xx_count", 250.0, Instant.now())),
                List.of("dialog-balance"),
                Instant.now().minusSeconds(300),
                Instant.now()
        );
        IncidentSummary s = service.summarizeIncident(input);
        assertThat(s.severity()).isEqualTo("P1");
        assertThat(s.suggestedActions()).isNotEmpty();
    }

    @Test
    @DisplayName("summarizeIncident assigns P2 for high latency")
    void summarize_p2() {
        IncidentInput input = new IncidentInput(
                List.of(new LogEntry("WARN", "Slow downstream", Instant.now())),
                List.of(new Metric("p95_latency_ms", 3000, Instant.now())),
                List.of(),
                Instant.now().minusSeconds(60),
                Instant.now()
        );
        IncidentSummary s = service.summarizeIncident(input);
        assertThat(s.severity()).isEqualTo("P2");
    }

    @Test
    @DisplayName("summarizeIncident detects downstream connectivity as root cause")
    void summarize_rootCauses() {
        IncidentInput input = new IncidentInput(
                List.of(
                        new LogEntry("ERROR", "Connection refused from upstream", Instant.now()),
                        new LogEntry("ERROR", "Connection timeout to BSS", Instant.now())
                ),
                List.of(),
                List.of("dialog-balance"),
                Instant.now().minusSeconds(60),
                Instant.now()
        );
        IncidentSummary s = service.summarizeIncident(input);
        assertThat(s.rootCauses()).isNotEmpty();
        assertThat(s.rootCauses().get(0)).contains("Downstream");
    }

    @Test
    @DisplayName("deduplicateAlerts merges by (service, alertName)")
    void dedupe() {
        Instant now = Instant.now();
        List<Alert> alerts = List.of(
                new Alert("payment-service", "HighErrorRate", "HIGH", now),
                new Alert("payment-service", "HighErrorRate", "HIGH", now),
                new Alert("payment-service", "LatencyP95", "MEDIUM", now),
                new Alert("usage-service", "HighErrorRate", "HIGH", now)
        );
        List<DeduplicatedAlert> result = service.deduplicateAlerts(alerts);
        assertThat(result).hasSize(3);
        // payment-service/HighErrorRate should have count=2
        var high = result.stream()
                .filter(d -> "payment-service|HighErrorRate".equals(d.key()))
                .findFirst().orElseThrow();
        assertThat(high.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("dedupe sorts by severity (HIGH before MEDIUM)")
    void dedupe_sortBySeverity() {
        List<Alert> alerts = List.of(
                new Alert("svc", "LowAlert", "LOW", Instant.now()),
                new Alert("svc", "HighAlert", "HIGH", Instant.now())
        );
        List<DeduplicatedAlert> result = service.deduplicateAlerts(alerts);
        assertThat(result.get(0).alertName()).isEqualTo("HighAlert");
    }

    @Test
    @DisplayName("forecastCapacity returns LINEAR for valid input")
    void forecast_capacity() {
        List<Double> data = List.of(10.0, 12.0, 14.0, 16.0, 18.0);
        CapacityForecast fc = service.forecastCapacity(data, 5);
        assertThat(fc.model()).isEqualTo("LINEAR");
        assertThat(fc.forecast()).hasSize(5);
        // Should be increasing
        assertThat(fc.forecast().get(0)).isGreaterThan(data.get(data.size() - 1));
    }

    @Test
    @DisplayName("forecastCapacity returns INSUFFICIENT_DATA for small input")
    void forecast_insufficient() {
        CapacityForecast fc = service.forecastCapacity(List.of(1.0, 2.0), 5);
        assertThat(fc.model()).isEqualTo("INSUFFICIENT_DATA");
    }

    // ----- Config / release correlation -----

    @Test
    @DisplayName("correlateConfigAndReleases returns NONE when no events in window")
    void correlate_none() {
        IncidentInput input = new IncidentInput(List.of(), List.of(),
                List.of("dialog-lk"), Instant.now().minusSeconds(60), Instant.now());
        OpsAiCopilotService.CorrelationResult r = service.correlateConfigAndReleases(
                "INC-1", Instant.now().minusSeconds(60), input, List.of(), List.of());
        assertThat(r.correlatedType()).isEqualTo("NONE");
        assertThat(r.confidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("correlateConfigAndReleases finds a config change 3 minutes before incident")
    void correlate_config() {
        Instant incidentStart = Instant.parse("2026-09-03T12:00:00Z");
        Instant configTime = incidentStart.minusSeconds(180); // 3 min before
        IncidentInput input = new IncidentInput(List.of(), List.of(),
                List.of("dialog-lk"), incidentStart.minusSeconds(60), incidentStart);
        OpsAiCopilotService.ConfigChangeEvent event = new OpsAiCopilotService.ConfigChangeEvent(
                "dialog-lk", "LAYOUT_PUBLISH", "layout:home", "5", "admin@omobio", configTime);
        OpsAiCopilotService.CorrelationResult r = service.correlateConfigAndReleases(
                "INC-2", incidentStart, input, List.of(event), List.of());
        assertThat(r.correlatedType()).isEqualTo("CONFIG_CHANGE");
        assertThat(r.confidence()).isGreaterThan(0.9);
        assertThat(r.minutesBeforeIncident()).isEqualTo(3L);
    }

    @Test
    @DisplayName("correlateConfigAndReleases prefers higher-confidence correlation")
    void correlate_picks_higher() {
        Instant incidentStart = Instant.parse("2026-09-03T12:00:00Z");
        Instant releaseTime = incidentStart.minusSeconds(60); // 1 min before
        Instant configTime = incidentStart.minusSeconds(110); // ~2 min before, lower confidence
        IncidentInput input = new IncidentInput(List.of(), List.of(),
                List.of("dialog-lk"), incidentStart.minusSeconds(60), incidentStart);
        OpsAiCopilotService.ConfigChangeEvent event = new OpsAiCopilotService.ConfigChangeEvent(
                "dialog-lk", "LAYOUT_PUBLISH", "layout:home", "5", "admin@omobio", configTime);
        OpsAiCopilotService.ReleaseEvent release = new OpsAiCopilotService.ReleaseEvent(
                "dialog-lk", "dashboard-bff", "1.2.3", "abc123", "ci-bot", releaseTime);
        OpsAiCopilotService.CorrelationResult r = service.correlateConfigAndReleases(
                "INC-3", incidentStart, input, List.of(event), List.of(release));
        // Release (1 min before, higher confidence) wins over config (110 min before)
        assertThat(r.correlatedType()).isEqualTo("RELEASE");
    }
}
