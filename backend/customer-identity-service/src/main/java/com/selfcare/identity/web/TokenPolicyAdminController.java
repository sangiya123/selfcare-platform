package com.selfcare.identity.web;

import com.selfcare.identity.domain.TenantTokenPolicy;
import com.selfcare.identity.service.TenantTokenPolicyService;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.platform.common.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin REST API for managing per-tenant token TTL policy (ADR-011).
 *
 * Only accessible to admin-role callers (enforced at the API gateway level via RBAC).
 * All changes are auditable via the AuditService.
 *
 * @see TenantTokenPolicyService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/token-policy")
@RequiredArgsConstructor
@Tag(name = "Token Policy Admin", description = "Per-tenant JWT token TTL configuration (ADR-011)")
public class TokenPolicyAdminController {

    private final TenantTokenPolicyService policyService;

    @GetMapping
    @Operation(summary = "Get the effective token policy for a tenant")
    public ResponseEntity<ApiResponse<TenantTokenPolicy>> getPolicy(
            @RequestParam String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(
                policyService.getEffectivePolicy(tenantId),
                TenantContext.get().getCorrelationId()));
    }

    @PutMapping
    @Operation(summary = "Upsert a tenant's token policy")
    public ResponseEntity<ApiResponse<TenantTokenPolicy>> upsertPolicy(
            @RequestBody TenantTokenPolicy policy) {
        return ResponseEntity.ok(ApiResponse.of(
                policyService.upsert(policy),
                TenantContext.get().getCorrelationId()));
    }

    @DeleteMapping
    @Operation(summary = "Delete a tenant's custom policy (revert to platform defaults)")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(
            @RequestParam String tenantId) {
        policyService.delete(tenantId);
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }
}
