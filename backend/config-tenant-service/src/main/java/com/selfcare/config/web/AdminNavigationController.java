package com.selfcare.config.web;

import com.selfcare.config.domain.NavigationDocument;
import com.selfcare.config.service.NavigationService;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for navigation documents.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/navigation")
@RequiredArgsConstructor
@Tag(name = "Admin Navigation", description = "Manage navigation configuration for mobile apps")
public class AdminNavigationController {

    private final NavigationService service;

    @GetMapping
    @Operation(summary = "List navigation documents for a tenant")
    public ResponseEntity<ApiResponse<List<NavigationDocument>>> list(@RequestParam String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(service.listForTenant(tenantId), ""));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get navigation document by ID")
    public ResponseEntity<ApiResponse<NavigationDocument>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id), ""));
    }

    @PostMapping
    @Operation(summary = "Save navigation document")
    public ResponseEntity<ApiResponse<NavigationDocument>> save(@RequestBody NavigationDocument navigation) {
        return ResponseEntity.ok(ApiResponse.of(service.save(navigation), ""));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish navigation document")
    public ResponseEntity<ApiResponse<NavigationDocument>> publish(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.publish(id), ""));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete navigation document")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}