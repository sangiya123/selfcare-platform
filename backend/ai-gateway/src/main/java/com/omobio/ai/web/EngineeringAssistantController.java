package com.omobio.ai.web;

import com.omobio.ai.service.EngineeringAssistantService;
import com.omobio.ai.service.EngineeringAssistantService.DependencyImpact;
import com.omobio.ai.service.EngineeringAssistantService.FlakyCluster;
import com.omobio.ai.service.EngineeringAssistantService.FlakyTestRecord;
import com.omobio.ai.service.EngineeringAssistantService.MockScenario;
import com.omobio.ai.service.EngineeringAssistantService.ReviewComment;
import com.omobio.ai.service.EngineeringAssistantService.TestSuggestion;
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
 * Engineering/QA AI REST API — implements AI scope section 5.
 *
 * Per spec: "AI-generated code/tests always pass normal review and
 * automated gates." Output is for human review, not auto-applied.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/eng-ai")
@RequiredArgsConstructor
@Tag(name = "Engineering AI", description = "Test gen, code review, mock scenarios, dependency impact, flaky clustering")
public class EngineeringAssistantController {

    private final EngineeringAssistantService engAi;

    @PostMapping("/tests")
    @Operation(summary = "Generate test case suggestions for a method")
    public ResponseEntity<ApiResponse<List<TestSuggestion>>> tests(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                engAi.generateTests(body.get("methodSignature"), body.get("methodBody")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/mock-scenarios")
    @Operation(summary = "Generate WireMock scenario stubs for an endpoint")
    public ResponseEntity<ApiResponse<List<MockScenario>>> mockScenarios(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                engAi.generateMockScenarios(body.getOrDefault("endpoint", "/api/v1/example"),
                        body.getOrDefault("method", "GET")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/review")
    @Operation(summary = "Static code review assistant")
    public ResponseEntity<ApiResponse<List<ReviewComment>>> review(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                engAi.reviewCode(body.get("code"), body.getOrDefault("language", "java")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/dependency-impact")
    @Operation(summary = "Analyze the impact of upgrading a dependency")
    public ResponseEntity<ApiResponse<DependencyImpact>> dependencyImpact(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                engAi.analyzeDependencyUpgrade(
                        body.get("dependency"),
                        body.get("fromVersion"),
                        body.get("toVersion")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/flaky-clusters")
    @Operation(summary = "Cluster flaky test records by failure pattern")
    public ResponseEntity<ApiResponse<List<FlakyCluster>>> flakyClusters(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.get("records");
        List<FlakyTestRecord> records = raw == null ? List.of() : raw.stream().map(r -> new FlakyTestRecord(
                String.valueOf(r.getOrDefault("testName", "")),
                String.valueOf(r.getOrDefault("outcome", "")),
                String.valueOf(r.getOrDefault("errorMessage", "")),
                java.time.Instant.now()
        )).toList();
        return ResponseEntity.ok(ApiResponse.of(
                engAi.clusterFlakyTests(records),
                TenantContext.get().getCorrelationId()));
    }
}
