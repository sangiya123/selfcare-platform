package com.selfcare.config.web;

import com.selfcare.config.domain.ProductMappingDocument;
import com.selfcare.config.service.ProductMappingService;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for product mappings.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/product-mapping")
@RequiredArgsConstructor
@Tag(name = "Admin Product Mapping", description = "Manage source-to-canonical product mappings")
public class AdminProductMappingController {

    private final ProductMappingService service;

    @GetMapping
    @Operation(summary = "List product mappings for a tenant")
    public ResponseEntity<ApiResponse<Page<ProductMappingDocument>>> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) String sourceProvider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProductMappingDocument> mappings = service.search(tenantId, sourceProvider, status, pageable);
        return ResponseEntity.ok(ApiResponse.of(mappings, ""));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product mapping by ID")
    public ResponseEntity<ApiResponse<ProductMappingDocument>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id), ""));
    }

    @PostMapping
    @Operation(summary = "Save product mapping")
    public ResponseEntity<ApiResponse<ProductMappingDocument>> save(@RequestBody ProductMappingDocument mapping) {
        return ResponseEntity.ok(ApiResponse.of(service.save(mapping), ""));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk save product mappings")
    public ResponseEntity<ApiResponse<List<ProductMappingDocument>>> bulkSave(@RequestBody List<ProductMappingDocument> mappings) {
        return ResponseEntity.ok(ApiResponse.of(service.bulkSave(mappings), ""));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Review product mapping (approve/reject)")
    public ResponseEntity<ApiResponse<ProductMappingDocument>> review(
            @PathVariable String id,
            @RequestParam boolean approved,
            @RequestParam String reviewer) {
        return ResponseEntity.ok(ApiResponse.of(service.review(id, approved, reviewer), ""));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (deprecate) product mapping")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}