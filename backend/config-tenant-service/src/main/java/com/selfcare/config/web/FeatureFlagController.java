package com.selfcare.config.web;

import com.selfcare.config.domain.FeatureFlag;
import com.selfcare.config.service.FeatureFlagService;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for feature flags.
 *
 * Endpoints:
 *   GET    /api/v1/admin/feature-flags?tenantId=...       — List flags
 *   GET    /api/v1/admin/feature-flags/{id}              — Get flag
 *   POST   /api/v1/admin/feature-flags                   — Create flag
 *   PUT    /api/v1/admin/feature-flags/{id}              — Update flag (gated for 100% rollout)
 *   DELETE /api/v1/admin/feature-flags/{id}              — Delete flag
 *
 * Four-eyes approval (FEATURE_ENABLE_100):
 *   Updating a flag's rollout to 100% requires four-eyes approval.
 *   The PUT endpoint blocks until an approver has decided.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/feature-flags")
@RequiredArgsConstructor
@Tag(name = "Admin Feature Flags", description = "Manage feature flags (100% rollout requires four-eyes approval)")
public class FeatureFlagController {

    private final FeatureFlagService service;

    @GetMapping
    @Operation(summary = "List feature flags for a tenant")
    public ResponseEntity<ApiResponse<List<FeatureFlag>>> list(@RequestParam String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(service.listForTenant(tenantId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get feature flag by ID")
    public ResponseEntity<ApiResponse<FeatureFlag>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new feature flag")
    public ResponseEntity<ApiResponse<FeatureFlag>> create(@RequestBody FeatureFlag flag) {
        return ResponseEntity.ok(ApiResponse.of(service.save(flag)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update feature flag",
            description = "Updating the rollout to 100% requires four-eyes approval. " +
                    "The request blocks until a decision is made.")
    public ResponseEntity<ApiResponse<FeatureFlag>> update(
            @PathVariable String id, @RequestBody FeatureFlag flag) {
        flag.setId(id);
        return ResponseEntity.ok(ApiResponse.of(service.save(flag)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete feature flag")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}
