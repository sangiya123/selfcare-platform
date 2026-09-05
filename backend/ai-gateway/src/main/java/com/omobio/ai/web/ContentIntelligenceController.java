package com.omobio.ai.web;

import com.omobio.ai.service.ContentIntelligenceService;
import com.omobio.ai.service.ContentIntelligenceService.AccessibilityReport;
import com.omobio.ai.service.ContentIntelligenceService.ContentResult;
import com.omobio.ai.service.ContentIntelligenceService.FaqItem;
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
 * Content Intelligence REST API — implements AI scope section 3.
 *
 * All endpoints return content that requires human approval before
 * production publication (low-risk automation only when policy allows).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/content-ai")
@RequiredArgsConstructor
@Tag(name = "Content AI", description = "Content rewrite, translate, accessibility, FAQ, campaign variants")
public class ContentIntelligenceController {

    private final ContentIntelligenceService contentAI;

    @PostMapping("/rewrite")
    @Operation(summary = "Rewrite operator content for clarity and tone")
    public ResponseEntity<ApiResponse<ContentResult>> rewrite(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();
        return ResponseEntity.ok(ApiResponse.of(
                contentAI.rewrite(content, tenantId, userId),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/translate")
    @Operation(summary = "Translate content to a target locale, applying industry terminology")
    public ResponseEntity<ApiResponse<ContentResult>> translate(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        String targetLocale = body.getOrDefault("targetLocale", "en");
        String industry = body.getOrDefault("industry", "telco");
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();
        return ResponseEntity.ok(ApiResponse.of(
                contentAI.translate(content, targetLocale, industry, tenantId, userId),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/accessibility")
    @Operation(summary = "Check image alt text and surrounding context for accessibility issues")
    public ResponseEntity<ApiResponse<AccessibilityReport>> accessibility(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                contentAI.accessibilityCheck(
                        body.get("imageDescription"),
                        body.get("surroundingContext"),
                        body.getOrDefault("industry", "telco")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/alt-text")
    @Operation(summary = "Suggest alt text for an image based on a description")
    public ResponseEntity<ApiResponse<String>> altText(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                contentAI.suggestAltText(body.get("imageDescription"),
                        body.getOrDefault("industry", "telco")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/campaign-variants")
    @Operation(summary = "Generate campaign copy variants for a promotion")
    public ResponseEntity<ApiResponse<List<String>>> campaignVariants(@RequestBody Map<String, Object> body) {
        String productName = (String) body.getOrDefault("productName", "your plan");
        String benefit = (String) body.getOrDefault("benefit", "great benefits");
        String industry = (String) body.getOrDefault("industry", "telco");
        int count = ((Number) body.getOrDefault("count", 4)).intValue();
        return ResponseEntity.ok(ApiResponse.of(
                contentAI.campaignVariants(productName, benefit, industry, count),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/faq")
    @Operation(summary = "Generate FAQ questions and answers from a knowledge base entry")
    public ResponseEntity<ApiResponse<List<FaqItem>>> generateFaq(@RequestBody Map<String, Object> body) {
        String knowledge = (String) body.get("content");
        int count = ((Number) body.getOrDefault("count", 5)).intValue();
        return ResponseEntity.ok(ApiResponse.of(
                contentAI.generateFaq(knowledge, count),
                TenantContext.get().getCorrelationId()));
    }
}
