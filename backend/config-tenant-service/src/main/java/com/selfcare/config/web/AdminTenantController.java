package com.selfcare.config.web;

import com.selfcare.config.domain.TenantConfig;
import com.selfcare.config.service.TenantConfigService;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.platform.common.web.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for tenant management.
 *
 * A "tenant" represents a business using the selfcare platform (Dialog, AIA, ...).
 * Each tenant has industry, country, supported LOBs, and a pack version.
 *
 * Endpoints:
 *   GET    /api/v1/admin/tenants                — List all tenants
 *   GET    /api/v1/admin/tenants?industry=...   — Filter by industry
 *   GET    /api/v1/admin/tenants/{tenantId}     — Get one
 *   POST   /api/v1/admin/tenants               — Create
 *   PUT    /api/v1/admin/tenants/{tenantId}     — Update
 *   DELETE /api/v1/admin/tenants/{tenantId}     — Delete
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "Admin Tenants", description = "Manage clients/tenants")
public class AdminTenantController {

    private final TenantConfigService service;

    @GetMapping
    @Operation(summary = "List tenants")
    public ResponseEntity<ApiResponse<List<TenantConfig>>> list(
            @RequestParam(required = false) String industry) {
        return ResponseEntity.ok(ApiResponse.of(service.listByIndustry(industry), ""));
    }

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get tenant by ID")
    public ResponseEntity<ApiResponse<TenantConfig>> get(@PathVariable String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(service.get(tenantId), ""));
    }

    @PostMapping
    @Operation(summary = "Create tenant")
    public ResponseEntity<ApiResponse<TenantConfig>> create(@RequestBody TenantConfig tenant) {
        return ResponseEntity.ok(ApiResponse.of(service.create(tenant), ""));
    }

    @PutMapping("/{tenantId}")
    @Operation(summary = "Update tenant")
    public ResponseEntity<ApiResponse<TenantConfig>> update(
            @PathVariable String tenantId,
            @RequestBody TenantConfig updates) {
        return ResponseEntity.ok(ApiResponse.of(service.update(tenantId, updates), ""));
    }

    @DeleteMapping("/{tenantId}")
    @Operation(summary = "Delete tenant")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String tenantId) {
        service.delete(tenantId);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}
