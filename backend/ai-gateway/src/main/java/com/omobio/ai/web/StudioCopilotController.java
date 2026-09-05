package com.omobio.ai.web;

import com.omobio.ai.service.StudioCopilotService;
import com.omobio.ai.service.StudioCopilotService.IntegrationMapping;
import com.omobio.ai.service.StudioCopilotService.LayoutVariant;
import com.omobio.ai.service.StudioCopilotService.ValidationExplanation;
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
 * Studio Copilot REST API — implements AI scope section 4.
 *
 * Per ADR-009: generated configurations reference only registered
 * components, actions, and connectors. All generated artifacts are
 * marked DRAFT and require human approval before publication.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/studio-copilot")
@RequiredArgsConstructor
@Tag(name = "Studio Copilot", description = "Page config, layout variants, journey drafts, integration mapping, migration assistance")
public class StudioCopilotController {

    private final StudioCopilotService copilot;

    @PostMapping("/page-config")
    @Operation(summary = "Generate a page configuration draft from a natural-language requirement")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pageConfig(@RequestBody Map<String, String> body) {
        String requirement = body.get("requirement");
        String industry = body.getOrDefault("industry", "telco");
        return ResponseEntity.ok(ApiResponse.of(
                copilot.generatePageConfig(requirement, industry),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/layout-variants")
    @Operation(summary = "Recommend layout variants for a page type")
    public ResponseEntity<ApiResponse<List<LayoutVariant>>> layoutVariants(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                copilot.recommendLayoutVariants(
                        body.getOrDefault("pageType", "home"),
                        body.getOrDefault("industry", "telco")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/visibility-rule")
    @Operation(summary = "Generate a visibility rule from a description")
    public ResponseEntity<ApiResponse<String>> visibilityRule(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                copilot.generateVisibilityRule(body.get("description")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/journey-draft")
    @Operation(summary = "Create a journey draft from an intent")
    public ResponseEntity<ApiResponse<Map<String, Object>>> journeyDraft(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.of(
                copilot.generateJourneyDraft(
                        (String) body.getOrDefault("intent", "Generic flow"),
                        ((Number) body.getOrDefault("steps", 3)).intValue()),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/integration-mapping")
    @Operation(summary = "Suggest canonical field mappings from a sample API payload")
    public ResponseEntity<ApiResponse<IntegrationMapping>> integrationMapping(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.getOrDefault("payload", body);
        return ResponseEntity.ok(ApiResponse.of(
                copilot.suggestIntegrationMapping(payload),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/explain-validation")
    @Operation(summary = "Explain config validation issues in human-readable form")
    public ResponseEntity<ApiResponse<List<ValidationExplanation>>> explainValidation(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) body.get("issues");
        return ResponseEntity.ok(ApiResponse.of(
                copilot.explainValidationIssues(issues),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/migration-notes")
    @Operation(summary = "Generate migration notes for legacy code (PHP/Java)")
    public ResponseEntity<ApiResponse<String>> migrationNotes(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                copilot.explainMigration(body.get("legacyCode"), body.getOrDefault("canonicalContract", "Provider")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/release-notes")
    @Operation(summary = "Generate release notes from a list of changes")
    public ResponseEntity<ApiResponse<String>> releaseNotes(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> changes = (List<String>) body.get("changes");
        String version = (String) body.getOrDefault("version", "1.0.0");
        return ResponseEntity.ok(ApiResponse.of(
                copilot.generateReleaseNotes(changes, version),
                TenantContext.get().getCorrelationId()));
    }
}
