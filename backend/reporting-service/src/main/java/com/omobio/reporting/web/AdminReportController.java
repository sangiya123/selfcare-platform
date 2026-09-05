package com.omobio.reporting.web;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.reporting.domain.ReportDefinition;
import com.omobio.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-facing report management controller.
 *
 * CRUD for report definitions and status management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    /**
     * List all report definitions for the current tenant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<?>>> listReports() {
        return ResponseEntity.ok(ApiResponse.of(reportService.listDefinitions()));
    }

    /**
     * Get a specific report definition.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getReport(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(reportService.getDefinition(id)));
    }

    /**
     * Create a new report definition.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReportDefinition>>
    createReport(@RequestBody ReportDefinition definition) {
        String adminId = TenantContext.get().getUserId();
        log.info("Creating report definition: name={}", definition.getName());
        ReportDefinition created = reportService.createDefinition(definition, adminId);
        return ResponseEntity.ok(ApiResponse.of(created));
    }

    /**
     * Update a report definition.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportDefinition>>
    updateReport(@PathVariable String id, @RequestBody ReportDefinition updated) {
        String adminId = TenantContext.get().getUserId();
        log.info("Updating report definition: id={}", id);
        ReportDefinition result = reportService.updateDefinition(id, updated, adminId);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    /**
     * Activate a report definition (DRAFT -> ACTIVE).
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<ReportDefinition>>
    activateReport(@PathVariable String id) {
        log.info("Activating report definition: id={}", id);
        return ResponseEntity.ok(ApiResponse.of(reportService.activateDefinition(id)));
    }

    /**
     * Disable a report definition.
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<ReportDefinition>>
    disableReport(@PathVariable String id) {
        log.info("Disabling report definition: id={}", id);
        return ResponseEntity.ok(ApiResponse.of(reportService.disableDefinition(id)));
    }
}
