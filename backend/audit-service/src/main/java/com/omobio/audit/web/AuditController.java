package com.omobio.audit.web;

import com.omobio.audit.domain.AuditEvent;
import com.omobio.audit.service.AuditService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
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
import java.time.Instant;

/**
 * Audit query controller — exposes audit events for the current tenant.
 *
 * Customer/admin users can query their tenant's audit history.
 *
 * @see AuditService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * Query audit events with optional filters.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditEvent>>> query(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Audit query: tenant={}, userId={}, action={}, resourceType={}, from={}, to={}",
                TenantContext.get().getTenantId(), userId, action, resourceType, from, to);

        return ResponseEntity.ok(ApiResponse.of(
                auditService.query(userId, action, resourceType, from, to, page, size)));
    }

    /**
     * Get a specific audit event by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEvent>> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(auditService.getById(id)));
    }

    /**
     * Export audit events as a CSV file.
     */
    @GetMapping("/export")
    public ResponseEntity<Resource> export(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        try {
            String filePath = auditService.exportCsv(userId, action, resourceType, from, to);
            File file = new File(filePath);
            log.info("Audit export: tenant={}, file={}", TenantContext.get().getTenantId(), filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getName() + "\"")
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            log.error("Audit export failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
