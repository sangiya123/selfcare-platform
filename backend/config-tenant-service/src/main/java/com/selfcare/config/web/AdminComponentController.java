package com.selfcare.config.web;

import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.service.ComponentCatalogService;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for the component catalog.
 *
 * Feeds the selfcare Studio component palette — labels, icons, categories,
 * platforms, security classification and enablement are authored here and
 * compiled into the Experience Manifest `components` section.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/components")
@RequiredArgsConstructor
@Tag(name = "Admin Components", description = "Manage the per-tenant component catalog")
public class AdminComponentController {

    private final ComponentCatalogService service;

    @GetMapping
    @Operation(summary = "List component catalog items for a tenant")
    public ResponseEntity<ApiResponse<List<ComponentCatalogItem>>> list(@RequestParam String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(service.listForTenant(tenantId), ""));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get catalog item by ID")
    public ResponseEntity<ApiResponse<ComponentCatalogItem>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id), ""));
    }

    @PostMapping
    @Operation(summary = "Create or update a catalog item (upsert by tenant+environment+componentId)")
    public ResponseEntity<ApiResponse<ComponentCatalogItem>> save(@RequestBody ComponentCatalogItem item) {
        return ResponseEntity.ok(ApiResponse.of(service.save(item), ""));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish a catalog item")
    public ResponseEntity<ApiResponse<ComponentCatalogItem>> publish(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.publish(id), ""));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a catalog item")
    public ResponseEntity<ApiResponse<ComponentCatalogItem>> archive(@PathVariable String id) {
        service.archive(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a catalog item")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}