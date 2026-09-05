package com.omobio.ai.web;

import com.omobio.ai.domain.PredictionResult;
import com.omobio.ai.service.PredictiveMLService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Predictive ML read-model API.
 *
 * Endpoints:
 *   GET /api/v1/predictions/{type}/targets/{targetType}/{targetId}
 *   GET /api/v1/predictions/{type}/high-risk?minLevel=3
 *
 * Per AI scope: served from a read model — synchronous LLM calls
 * are NOT in the dashboard critical path.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
@Tag(name = "Predictions", description = "Predictive ML — churn, exhaustion, bill shock, etc.")
public class PredictionController {

    private final PredictiveMLService predictiveMLService;

    @GetMapping("/{type}/targets/{targetType}/{targetId}")
    @Operation(summary = "Get the latest prediction for a target",
            description = "Returns the latest prediction of the given type for a target (connection / user / policy).")
    public ResponseEntity<ApiResponse<PredictionResult>> getPrediction(
            @PathVariable String type,
            @PathVariable String targetType,
            @PathVariable String targetId) {
        String tenantId = TenantContext.get().getTenantId();
        PredictionResult result = predictiveMLService.getLatest(tenantId, targetType, targetId, type)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.of(result, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/targets/{targetType}/{targetId}")
    @Operation(summary = "Get all active predictions for a target")
    public ResponseEntity<ApiResponse<List<PredictionResult>>> getAllForTarget(
            @PathVariable String targetType,
            @PathVariable String targetId) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                predictiveMLService.getActiveForTarget(tenantId, targetType, targetId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/{type}/high-risk")
    @Operation(summary = "List high/critical risk connections for a prediction type",
            description = "minLevel: 1=LOW+, 2=MEDIUM+, 3=HIGH+, 4=CRITICAL only")
    public ResponseEntity<ApiResponse<List<PredictionResult>>> getHighRisk(
            @PathVariable String type,
            @RequestParam(defaultValue = "3") int minLevel) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                predictiveMLService.getHighRiskConnections(tenantId, type, minLevel),
                TenantContext.get().getCorrelationId()));
    }
}
