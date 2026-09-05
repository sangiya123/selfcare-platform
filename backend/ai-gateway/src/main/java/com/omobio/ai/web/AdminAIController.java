package com.omobio.ai.web;

import com.omobio.ai.service.*;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Admin AI Controller — admin-only endpoints for managing AI configuration.
 *
 * Auth: requires admin JWT (validated upstream by api-gateway).
 *
 * Endpoints:
 *   GET    /api/v1/admin/ai/config              — current AI config for tenant
 *   PUT    /api/v1/admin/ai/config              — update AI config
 *   GET    /api/v1/admin/ai/tools               — list tool definitions
 *   PUT    /api/v1/admin/ai/tools/{name}        — update tool permission
 *   GET    /api/v1/admin/ai/prompts             — list prompt templates
 *   GET    /api/v1/admin/ai/prompts/{id}        — get template
 *   PUT    /api/v1/admin/ai/prompts/{id}        — update template
 *   GET    /api/v1/admin/ai/usage               — usage metrics
 *   POST   /api/v1/admin/ai/knowledge           — index knowledge chunk
 *   GET    /api/v1/admin/ai/knowledge           — list knowledge chunks
 *   DELETE /api/v1/admin/ai/knowledge/{id}      — remove knowledge chunk
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@Tag(name = "Admin AI", description = "Admin endpoints for AI configuration")
public class AdminAIController {

    private final PromptTemplateService promptTemplateService;
    private final ToolPermissionService toolPermissionService;
    private final TokenUsageService tokenUsageService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final LlmProviderRouter llmProviderRouter;

    // -------------------------------------------------------------------------
    // AI configuration
    // -------------------------------------------------------------------------

    @GetMapping("/config")
    @Operation(summary = "Get current AI config for tenant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig() {
        String tenantId = TenantContext.get().getTenantId();
        Map<String, Object> config = new HashMap<>();
        config.put("provider", llmProviderRouter.switchProvider(tenantId));
        config.put("moderationEnabled", true);
        config.put("ragEnabled", true);
        config.put("toolsEnabled", true);
        config.put("usage", tokenUsageService.getTenantUsageToday(tenantId));
        return ResponseEntity.ok(ApiResponse.of(config, TenantContext.get().getCorrelationId()));
    }

    @PutMapping("/config")
    @Operation(summary = "Update AI config for tenant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateConfig(
            @RequestBody Map<String, Object> config) {
        log.info("Admin updated AI config: tenant={}, config={}",
                TenantContext.get().getTenantId(), config);
        return ResponseEntity.ok(ApiResponse.of(config, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Tools management
    // -------------------------------------------------------------------------

    @GetMapping("/tools")
    @Operation(summary = "List all tool definitions")
    public ResponseEntity<ApiResponse<List<ToolPermissionService.ToolDefinition>>> listTools(
            @RequestParam(required = false) String userId) {
        String tenantId = TenantContext.get().getTenantId();
        List<ToolPermissionService.ToolDefinition> tools =
                toolPermissionService.getAllowedTools(tenantId, userId);
        return ResponseEntity.ok(ApiResponse.of(tools, TenantContext.get().getCorrelationId()));
    }

    @PutMapping("/tools/{name}")
    @Operation(summary = "Update a tool permission for a user (or tenant default if userId omitted)")
    public ResponseEntity<ApiResponse<Void>> updateTool(
            @PathVariable String name,
            @RequestParam(required = false) String userId,
            @RequestBody ToolUpdateRequest request) {
        String tenantId = TenantContext.get().getTenantId();
        toolPermissionService.setPermission(tenantId, userId, name, request.permission());
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Prompt templates
    // -------------------------------------------------------------------------

    @GetMapping("/prompts")
    @Operation(summary = "List all available prompt template IDs")
    public ResponseEntity<ApiResponse<List<String>>> listPromptTemplates() {
        String tenantId = TenantContext.get().getTenantId();
        List<String> ids = promptTemplateService.listTemplateIds(tenantId);
        return ResponseEntity.ok(ApiResponse.of(ids, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/prompts/{id}")
    @Operation(summary = "Get raw prompt template content")
    public ResponseEntity<ApiResponse<PromptResponse>> getPrompt(@PathVariable String id) {
        String tenantId = TenantContext.get().getTenantId();
        String content = promptTemplateService.getTemplate(id, tenantId);
        return ResponseEntity.ok(ApiResponse.of(
                new PromptResponse(id, content, false),
                TenantContext.get().getCorrelationId()));
    }

    @PutMapping("/prompts/{id}")
    @Operation(summary = "Update a prompt template override for this tenant")
    public ResponseEntity<ApiResponse<PromptResponse>> updatePrompt(
            @PathVariable String id,
            @RequestBody PromptUpdateRequest request) {
        String tenantId = TenantContext.get().getTenantId();
        promptTemplateService.saveTemplate(tenantId, id, request.content());
        return ResponseEntity.ok(ApiResponse.of(
                new PromptResponse(id, request.content(), true),
                TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Usage metrics
    // -------------------------------------------------------------------------

    @GetMapping("/usage")
    @Operation(summary = "Get usage metrics for the current tenant")
    public ResponseEntity<ApiResponse<UsageResponse>> getUsage() {
        String tenantId = TenantContext.get().getTenantId();
        Map<Object, Object> today = tokenUsageService.getTenantUsageToday(tenantId);

        long inputTokens = toLong(today.get("inputTokens"));
        long outputTokens = toLong(today.get("outputTokens"));
        long requestCount = toLong(today.get("requestCount"));

        // Estimate cost (using Claude pricing as default)
        AIResponse.TokenUsage usage = AIResponse.TokenUsage.builder()
                .promptTokens((int) inputTokens)
                .completionTokens((int) outputTokens)
                .totalTokens((int) (inputTokens + outputTokens))
                .build();
        BigDecimal cost = tokenUsageService.estimateCost("claude-sonnet-4-5", usage);

        return ResponseEntity.ok(ApiResponse.of(
                new UsageResponse(tenantId, inputTokens, outputTokens, requestCount, cost),
                TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Knowledge base management
    // -------------------------------------------------------------------------

    @PostMapping("/knowledge")
    @Operation(summary = "Index a new knowledge chunk for RAG")
    public ResponseEntity<ApiResponse<KnowledgeIndexResponse>> indexKnowledge(
            @RequestBody KnowledgeIndexRequest request) {
        String tenantId = TenantContext.get().getTenantId();
        String chunkId = "chunk-" + UUID.randomUUID();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenantId", tenantId);
        metadata.put("source", request.source() != null ? request.source() : "manual");
        if (request.tags() != null) {
            request.tags().forEach((k, v) -> metadata.put("tag:" + k, v));
        }

        vectorEmbeddingService.indexChunk(chunkId, request.text(), metadata);

        return ResponseEntity.ok(ApiResponse.of(
                new KnowledgeIndexResponse(chunkId, "INDEXED"),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/knowledge")
    @Operation(summary = "Test similarity search against the knowledge base")
    public ResponseEntity<ApiResponse<List<VectorEmbeddingService.SimilarChunk>>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        String tenantId = TenantContext.get().getTenantId();
        List<VectorEmbeddingService.SimilarChunk> results =
                vectorEmbeddingService.findSimilar(query, tenantId, topK);
        return ResponseEntity.ok(ApiResponse.of(results, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Request/Response records
    // -------------------------------------------------------------------------

    public record ToolUpdateRequest(ToolPermissionService.ToolPermission permission) {}

    public record PromptUpdateRequest(String content) {}

    public record PromptResponse(String id, String content, boolean isCustomOverride) {}

    public record UsageResponse(
            String tenantId,
            long inputTokensToday,
            long outputTokensToday,
            long requestCountToday,
            BigDecimal estimatedCostUsd
    ) {}

    public record KnowledgeIndexRequest(String text, String source, Map<String, String> tags) {}

    public record KnowledgeIndexResponse(String chunkId, String status) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0; }
    }
}
