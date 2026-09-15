package com.selfcare.reporting.web;

import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.reporting.domain.ReportExecution;
import com.selfcare.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

/**
 * Customer-facing report controller.
 *
 * Endpoints for listing reports and executions, and downloading results.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * List all report definitions visible to the current tenant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> listReports() {
        return ResponseEntity.ok(ApiResponse.of(reportService.listDefinitions()));
    }

    /**
     * Get a specific report definition.
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<?>> getReport(@PathVariable String reportId) {
        return ResponseEntity.ok(ApiResponse.of(reportService.getDefinition(reportId)));
    }

    /**
     * Trigger an ad-hoc execution of a report.
     *
     * @param reportId   the report to run
     * @param parameters JSON body of parameter values
     * @return the execution record
     */
    @PostMapping("/{reportId}/execute")
    public ResponseEntity<ApiResponse<ReportExecution>>
    executeReport(@PathVariable String reportId,
                 @RequestBody(required = false) java.util.Map<String, Object> parameters) {
        String triggeredBy = com.selfcare.platform.common.tenant.TenantContext.get().getUserId();
        String paramsJson = parameters != null
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(parameters).toString()
                : "{}";
        log.info("Triggering report execution: reportId={}", reportId);
        ReportExecution execution = reportService.triggerExecution(reportId, paramsJson, triggeredBy);
        return ResponseEntity.accepted().body(ApiResponse.of(execution));
    }

    /**
     * List executions for a specific report.
     */
    @GetMapping("/{reportId}/executions")
    public ResponseEntity<ApiResponse<Page<ReportExecution>>>
    listExecutions(@PathVariable String reportId,
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
                reportService.listExecutions(reportId, page, size)));
    }

    /**
     * Get a specific execution by ID.
     */
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<ApiResponse<ReportExecution>>
    getExecution(@PathVariable String executionId) {
        return ResponseEntity.ok(ApiResponse.of(reportService.getExecution(executionId)));
    }

    /**
     * Download a completed report result.
     *
     * Returns the rendered file (CSV, JSON, EXCEL, or PDF stub).
     */
    @GetMapping("/executions/{executionId}/download")
    public ResponseEntity<Resource> downloadExecution(@PathVariable String executionId) {
        ReportExecution execution = reportService.getExecution(executionId);
        if (!"COMPLETED".equals(execution.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        // Build path from the execution's result URL
        // The ReportRenderer writes to {outputDir}/{tenantId}/{filename}
        // We reconstruct the file path from the execution record
        String filename = extractFilename(execution.getResultUrl());
        File file = new File(
                com.selfcare.platform.common.tenant.TenantContext.get().getTenantId()
                        + "/" + filename);

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = getContentType(execution.getResultUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .body(new FileSystemResource(file));
    }

    private String extractFilename(String resultUrl) {
        if (resultUrl == null) return "report.csv";
        int lastSlash = resultUrl.lastIndexOf('/');
        return lastSlash >= 0 ? resultUrl.substring(lastSlash + 1) : resultUrl;
    }

    private String getContentType(String url) {
        if (url == null) return "application/octet-stream";
        if (url.endsWith(".csv")) return "text/csv";
        if (url.endsWith(".json")) return "application/json";
        if (url.endsWith(".xls")) return "application/vnd.ms-excel";
        if (url.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
