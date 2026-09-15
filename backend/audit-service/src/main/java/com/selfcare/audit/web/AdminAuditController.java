package com.selfcare.audit.web;

import com.selfcare.audit.domain.AuditEvent;
import com.selfcare.audit.repository.AuditEventRepository;
import com.selfcare.audit.service.AuditService;
import com.selfcare.platform.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.Instant;

/**
 * Admin audit controller — allows SUPER_ADMIN users to search audit events
 * across all tenants.
 *
 * @see AuditService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditEventRepository repository;
    private final AuditService auditService;

    /**
     * Search audit events across all tenants.
     *
     * This endpoint is intended for SUPER_ADMIN users only.
     *
     * @param userId       optional filter by user ID (searched across all tenants)
     * @param action       optional filter by action
     * @param resourceType optional filter by resource type
     * @param from         optional start date
     * @param to           optional end date
     * @param page         page number (0-indexed)
     * @param size         page size (max 200)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditEvent>>>
    adminSearch(@RequestParam(required = false) String userId,
                @RequestParam(required = false) String action,
                @RequestParam(required = false) String resourceType,
                @RequestParam(required = false) Instant from,
                @RequestParam(required = false) Instant to,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "50") int size) {

        log.info("Admin audit search: userId={}, action={}, resourceType={}", userId, action, resourceType);
        size = Math.min(size, 200);
        PageRequest pageable = PageRequest.of(page, size);

        Page<AuditEvent> results = repository.adminSearch(
                userId, action, resourceType, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.of(results));
    }

    /**
     * Get a specific audit event by ID (admin view, no tenant restriction).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEvent>> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(auditService.getById(id)));
    }

    /**
     * Admin export — exports across all tenants.
     */
    @GetMapping("/export")
    public ResponseEntity<Resource>
    adminExport(@RequestParam(required = false) String userId,
                @RequestParam(required = false) String action,
                @RequestParam(required = false) String resourceType,
                @RequestParam(required = false) Instant from,
                @RequestParam(required = false) Instant to) {

        try {
            // For admin export, use the adminSearch path
            int maxExportRows = 100_000;
            PageRequest pageable = PageRequest.of(0, maxExportRows);
            Page<AuditEvent> page = repository.adminSearch(
                    userId, action, resourceType, from, to, pageable);

            java.nio.file.Path exportDir = java.nio.file.Paths.get(
                    System.getProperty("java.io.tmpdir"), "audit-exports");
            java.nio.file.Files.createDirectories(exportDir);

            String timestamp = java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    .replace(":", "-").replace(".", "-");
            String filename = String.format("admin_audit_export_%s.csv", timestamp);
            java.nio.file.Path filePath = exportDir.resolve(filename);

            try (java.io.BufferedWriter writer =
                         java.nio.file.Files.newBufferedWriter(filePath)) {
                writer.write("id,tenant_id,user_id,action,resource_type,resource_id," +
                        "correlation_id,ip_address,severity,message,occurred_at,recorded_at");
                writer.newLine();
                for (AuditEvent event : page.getContent()) {
                    writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                            nullSafe(event.getId()),
                            nullSafe(event.getTenantId()),
                            nullSafe(event.getUserId()),
                            nullSafe(event.getAction()),
                            nullSafe(event.getResourceType()),
                            nullSafe(event.getResourceId()),
                            nullSafe(event.getCorrelationId()),
                            nullSafe(event.getIpAddress()),
                            nullSafe(event.getSeverity()),
                            escapeCsv(nullSafe(event.getMessage())),
                            event.getOccurredAt() != null ? event.getOccurredAt().toString() : "",
                            event.getRecordedAt() != null ? event.getRecordedAt().toString() : ""));
                    writer.newLine();
                }
            }

            log.info("Admin audit export: rows={}, file={}", page.getNumberOfElements(), filePath);
            File file = filePath.toFile();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getName() + "\"")
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            log.error("Admin audit export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
