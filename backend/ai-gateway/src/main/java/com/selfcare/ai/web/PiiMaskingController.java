package com.selfcare.ai.web;

import com.selfcare.ai.service.PiiMaskingService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PII Masking REST API.
 *
 * Used by other services to mask PII before logging or storing telemetry.
 * Also exposed for testing and admin visibility.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pii")
@RequiredArgsConstructor
@Tag(name = "PII Masking", description = "PII classification and masking for logs/telemetry")
public class PiiMaskingController {

    private final PiiMaskingService piiMasking;

    @PostMapping("/mask")
    @Operation(summary = "Mask PII in a text string")
    public ResponseEntity<ApiResponse<String>> maskText(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                piiMasking.mask(body.get("text")),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/mask-map")
    @Operation(summary = "Recursively mask PII in a structured payload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> maskMap(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(ApiResponse.of(
                piiMasking.maskMap(payload),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/classify")
    @Operation(summary = "Detect and classify PII without masking")
    public ResponseEntity<ApiResponse<List<PiiMaskingService.PiiMatch>>> classify(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.of(
                piiMasking.classify(body.get("text")),
                TenantContext.get().getCorrelationId()));
    }
}
