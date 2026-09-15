package com.selfcare.ai.web;

import com.selfcare.ai.service.AIModelGateway;
import com.selfcare.ai.service.AIResponse;
import com.selfcare.ai.service.ChatRequest;
import com.selfcare.ai.service.IntentClassification;
import com.selfcare.ai.service.RecommendationService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Gateway REST API.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Gateway", description = "AI chat, recommendations, intent classification")
public class AIGatewayController {

    private final AIModelGateway aiGateway;
    private final RecommendationService recommendationService;

    @PostMapping("/chat")
    @Operation(summary = "Chat with AI assistant")
    public ResponseEntity<ApiResponse<AIResponse>> chat(
            @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                aiGateway.chat(request), TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/classify")
    @Operation(summary = "Classify customer intent")
    public ResponseEntity<ApiResponse<IntentClassification>> classify(
            @RequestBody ClassifyRequest request) {
        IntentClassification intent = aiGateway.classifyIntent(
                request.message, TenantContext.get().getTenantId());
        return ResponseEntity.ok(ApiResponse.of(intent, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/recommendations/{connectionId}")
    @Operation(summary = "Get bundle recommendations for a connection")
    public ResponseEntity<ApiResponse<List<RecommendationService.BundleRecommendation>>> recommendBundles(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(ApiResponse.of(
                recommendationService.recommendBundles(connectionId, limit),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/churn/{connectionId}")
    @Operation(summary = "Score churn risk for a connection")
    public ResponseEntity<ApiResponse<RecommendationService.ChurnRiskScore>> scoreChurnRisk(@PathVariable String connectionId) {
        return ResponseEntity.ok(ApiResponse.of(
                recommendationService.scoreChurnRisk(connectionId),
                TenantContext.get().getCorrelationId()));
    }

    @lombok.Data
    public static class ClassifyRequest {
        private String message;
    }
}