package com.omobio.config.web;

import com.omobio.config.domain.ThemeDocument;
import com.omobio.config.service.ThemeService;
import com.omobio.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin REST API for theme documents.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/themes")
@RequiredArgsConstructor
@Tag(name = "Admin Themes", description = "Manage theme design tokens")
public class AdminThemeController {

    private final ThemeService service;

    @GetMapping
    @Operation(summary = "List themes for a tenant")
    public ResponseEntity<ApiResponse<List<ThemeDocument>>> list(@RequestParam String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(service.listForTenant(tenantId), ""));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get theme by ID")
    public ResponseEntity<ApiResponse<ThemeDocument>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id), ""));
    }

    @PostMapping
    @Operation(summary = "Save theme")
    public ResponseEntity<ApiResponse<ThemeDocument>> save(@RequestBody ThemeDocument theme) {
        return ResponseEntity.ok(ApiResponse.of(service.save(theme), ""));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish theme")
    public ResponseEntity<ApiResponse<ThemeDocument>> publish(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.publish(id), ""));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete theme")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}
