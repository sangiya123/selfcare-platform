package com.omobio.ai.web;

import com.omobio.ai.service.OpsAiCopilotService;
import com.omobio.ai.service.OpsAiCopilotService.Alert;
import com.omobio.ai.service.OpsAiCopilotService.CapacityForecast;
import com.omobio.ai.service.OpsAiCopilotService.DeduplicatedAlert;
import com.omobio.ai.service.OpsAiCopilotService.IncidentInput;
import com.omobio.ai.service.OpsAiCopilotService.IncidentSummary;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Operations AI Copilot REST API — implements AI scope section 6.
 *
 * Per spec: read-only / advisory. No autonomous production change.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ops-ai")
@RequiredArgsConstructor
@Tag(name = "Operations AI", description = "Incident summary, alert dedup, capacity forecast")
public class OpsAiCopilotController {

    private final OpsAiCopilotService opsAi;

    @PostMapping("/incident-summary")
    @Operation(summary = "Summarize an incident from logs and metrics")
    public ResponseEntity<ApiResponse<IncidentSummary>> incidentSummary(@RequestBody IncidentInput input) {
        return ResponseEntity.ok(ApiResponse.of(
                opsAi.summarizeIncident(input),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/alerts/dedupe")
    @Operation(summary = "Deduplicate a list of alerts by (service, alertName) and rank by severity")
    public ResponseEntity<ApiResponse<List<DeduplicatedAlert>>> dedupeAlerts(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.get("alerts");
        List<Alert> alerts = raw == null ? List.of() : raw.stream().map(a -> new Alert(
                String.valueOf(a.getOrDefault("service", "")),
                String.valueOf(a.getOrDefault("alertName", "")),
                String.valueOf(a.getOrDefault("severity", "MEDIUM")),
                java.time.Instant.now()
        )).toList();
        return ResponseEntity.ok(ApiResponse.of(
                opsAi.deduplicateAlerts(alerts),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/capacity-forecast")
    @Operation(summary = "Forecast metric capacity using linear regression")
    public ResponseEntity<ApiResponse<CapacityForecast>> capacityForecast(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Double> historical = (List<Double>) body.get("historical");
        int horizon = ((Number) body.getOrDefault("horizonDays", 30)).intValue();
        return ResponseEntity.ok(ApiResponse.of(
                opsAi.forecastCapacity(historical, horizon),
                TenantContext.get().getCorrelationId()));
    }
}
