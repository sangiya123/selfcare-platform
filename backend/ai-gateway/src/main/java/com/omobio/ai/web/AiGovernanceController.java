package com.omobio.ai.web;

import com.omobio.ai.domain.AiEvaluationResult;
import com.omobio.ai.domain.AiKillSwitch;
import com.omobio.ai.domain.AiPromptVersion;
import com.omobio.ai.domain.AiUseCase;
import com.omobio.ai.service.AiEvaluationService;
import com.omobio.ai.service.AiGovernanceService;
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
 * AI Governance REST API — admin endpoints to manage use cases, kill switches,
 * and evaluation results. Per the AI governance spec, kill-switch and policy
 * changes are auditable.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-governance")
@RequiredArgsConstructor
@Tag(name = "AI Governance", description = "Use case registry, kill switches, evaluation results, release gate")
public class AiGovernanceController {

    private final AiGovernanceService governance;
    private final AiEvaluationService evaluation;

    // ----- Use case registry -----

    @PostMapping("/use-cases")
    @Operation(summary = "Register a new AI use case")
    public ResponseEntity<ApiResponse<AiUseCase>> registerUseCase(@RequestBody AiUseCase useCase) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.registerUseCase(useCase),
                TenantContext.get().getCorrelationId()));
    }

    @PutMapping("/use-cases/{useCaseId}")
    @Operation(summary = "Update an existing use case")
    public ResponseEntity<ApiResponse<AiUseCase>> updateUseCase(@PathVariable String useCaseId,
                                                                @RequestBody AiUseCase body) {
        body.setUseCaseId(useCaseId);
        return ResponseEntity.ok(ApiResponse.of(
                governance.updateUseCase(body),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/use-cases/{useCaseId}")
    @Operation(summary = "Get a use case by id")
    public ResponseEntity<ApiResponse<AiUseCase>> getUseCase(@PathVariable String useCaseId) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.getUseCase(useCaseId).orElse(null),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/use-cases")
    @Operation(summary = "List use cases by risk tier (optional)")
    public ResponseEntity<ApiResponse<List<AiUseCase>>> listByRiskTier(
            @RequestParam(required = false) String riskTier) {
        List<AiUseCase> result = riskTier == null
                ? List.of()
                : governance.listByRiskTier(riskTier);
        return ResponseEntity.ok(ApiResponse.of(result, TenantContext.get().getCorrelationId()));
    }

    // ----- Kill switches -----

    @PostMapping("/kill-switches")
    @Operation(summary = "Activate a kill switch (USE_CASE or PROVIDER scope)")
    public ResponseEntity<ApiResponse<AiKillSwitch>> activateKillSwitch(@RequestBody Map<String, Object> body) {
        String scope = String.valueOf(body.getOrDefault("scope", "USE_CASE"));
        String useCaseId = (String) body.get("useCaseId");
        String provider = (String) body.get("provider");
        String reason = (String) body.getOrDefault("reason", "");
        String activatedBy = (String) body.getOrDefault("activatedBy", "admin");
        java.time.Instant expiresAt = body.get("expiresAt") != null
                ? java.time.Instant.parse((String) body.get("expiresAt"))
                : null;
        return ResponseEntity.ok(ApiResponse.of(
                governance.activateKillSwitch(scope, useCaseId, provider, reason, activatedBy, expiresAt),
                TenantContext.get().getCorrelationId()));
    }

    @DeleteMapping("/kill-switches/{killSwitchId}")
    @Operation(summary = "Deactivate a kill switch")
    public ResponseEntity<ApiResponse<Boolean>> deactivate(@PathVariable String killSwitchId) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.deactivateKillSwitch(killSwitchId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/kill-switches")
    @Operation(summary = "List active kill switches for the tenant")
    public ResponseEntity<ApiResponse<List<AiKillSwitch>>> listActive() {
        return ResponseEntity.ok(ApiResponse.of(
                governance.listActive(),
                TenantContext.get().getCorrelationId()));
    }

    // ----- Evaluation results & release gate -----

    @PostMapping("/evaluations")
    @Operation(summary = "Record an evaluation result")
    public ResponseEntity<ApiResponse<AiEvaluationResult>> recordEval(@RequestBody AiEvaluationResult result) {
        return ResponseEntity.ok(ApiResponse.of(
                evaluation.recordResult(result),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/evaluations/{useCaseId}/latest")
    @Operation(summary = "Get the latest evaluation for a use case")
    public ResponseEntity<ApiResponse<AiEvaluationResult>> latestEval(@PathVariable String useCaseId) {
        return ResponseEntity.ok(ApiResponse.of(
                evaluation.latestFor(useCaseId).orElse(null),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/evaluations/{useCaseId}/history")
    @Operation(summary = "Get recent evaluation history")
    public ResponseEntity<ApiResponse<List<AiEvaluationResult>>> evalHistory(
            @PathVariable String useCaseId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.of(
                evaluation.historyFor(useCaseId, limit),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/release-gate/{useCaseId}")
    @Operation(summary = "Check the release gate for a use case")
    public ResponseEntity<ApiResponse<AiEvaluationService.ReleaseGateResult>> releaseGate(
            @PathVariable String useCaseId) {
        return ResponseEntity.ok(ApiResponse.of(
                evaluation.checkReleaseGate(useCaseId),
                TenantContext.get().getCorrelationId()));
    }

    // ----- Prompt version management -----

    @PostMapping("/prompt-versions")
    @Operation(summary = "Create a new prompt template version")
    public ResponseEntity<ApiResponse<AiPromptVersion>> createPromptVersion(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.createPromptVersion(
                        body.get("templateId"),
                        body.get("content"),
                        body.get("industry"),
                        body.get("changeNotes"),
                        body.getOrDefault("createdBy",
                                TenantContext.get().getUserId() != null
                                        ? TenantContext.get().getUserId() : "admin")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/prompt-versions/{templateId}/activate/{version}")
    @Operation(summary = "Activate a specific prompt version (deactivates all others)")
    public ResponseEntity<ApiResponse<AiPromptVersion>> activatePromptVersion(
            @PathVariable String templateId,
            @PathVariable Integer version) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.activatePromptVersion(templateId, version),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/prompt-versions/{templateId}")
    @Operation(summary = "List all versions of a prompt template")
    public ResponseEntity<ApiResponse<List<AiPromptVersion>>> listPromptVersions(
            @PathVariable String templateId) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.listPromptVersions(templateId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/prompt-versions/{templateId}/active")
    @Operation(summary = "Get the currently active prompt version for a template")
    public ResponseEntity<ApiResponse<AiPromptVersion>> getActivePromptVersion(
            @PathVariable String templateId) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.getActivePromptVersion(templateId).orElse(null),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/prompt-versions/{templateId}/diff")
    @Operation(summary = "Compare two prompt versions by content hash")
    public ResponseEntity<ApiResponse<AiGovernanceService.PromptVersionDiff>> diffPromptVersions(
            @PathVariable String templateId,
            @RequestParam int v1,
            @RequestParam int v2) {
        return ResponseEntity.ok(ApiResponse.of(
                governance.diffPromptVersions(templateId, v1, v2),
                TenantContext.get().getCorrelationId()));
    }
}
