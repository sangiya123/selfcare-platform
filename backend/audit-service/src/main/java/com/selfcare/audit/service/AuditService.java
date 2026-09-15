package com.selfcare.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.audit.domain.AuditEvent;
import com.selfcare.audit.repository.AuditEventRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Audit service — records and queries immutable audit events.
 *
 * Events are always append-only. The service records events from:
 * - Direct API calls ({@link #record})
 * - Kafka listeners ({@link com.selfcare.audit.kafka.AuditEventListener})
 *
 * @see AuditEvent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${selfcare.audit.max-export-rows:100000}")
    private int maxExportRows = 100000;

    /**
     * Record an audit event.
     *
     * @param event the event to record; ID and recordedAt are set here
     * @return the saved event
     */
    @Transactional
    public AuditEvent record(AuditEvent event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID().toString());
        }
        if (event.getRecordedAt() == null) {
            event.setRecordedAt(Instant.now());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(event.getRecordedAt());
        }
        if (event.getTenantId() == null) {
            event.setTenantId(TenantContext.get().getTenantId());
        }

        AuditEvent saved = repository.save(event);
        log.debug("Audit event recorded: id={}, action={}, tenant={}, user={}",
                saved.getId(), saved.getAction(), saved.getTenantId(), saved.getUserId());
        return saved;
    }

    /**
     * Record an event with convenience builder pattern.
     *
     * @param action       the action name
     * @param resourceType the resource type
     * @param resourceId  the resource ID
     * @param severity    the outcome severity
     * @param message     optional message
     * @return the saved event
     */
    @Transactional
    public AuditEvent recordAction(String action, String resourceType, String resourceId,
                                   AuditEvent.Severity severity, String message) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(TenantContext.get().getTenantId())
                .userId(TenantContext.get().getUserId())
                .sessionId(TenantContext.get().getSessionId())
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .correlationId(TenantContext.get().getCorrelationId())
                .severity(severity != null ? severity.name() : AuditEvent.Severity.INFO.name())
                .message(message)
                .occurredAt(Instant.now())
                .recordedAt(Instant.now())
                .build();
        return record(event);
    }

    /**
     * Get a single audit event by ID.
     *
     * @param id the event ID
     * @return the event
     * @throws NotFoundException if not found
     */
    public AuditEvent getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("AuditEvent", id));
    }

    /**
     * Query audit events with optional filters for the current tenant.
     *
     * @param userId       filter by user ID (optional)
     * @param action       filter by action (optional)
     * @param resourceType filter by resource type (optional)
     * @param from         start of date range (optional)
     * @param to           end of date range (optional)
     * @param page         page number (0-indexed)
     * @param size         page size (max 200)
     * @return paginated audit events
     */
    public Page<AuditEvent> query(String userId, String action, String resourceType,
                                  Instant from, Instant to, int page, int size) {
        String tenantId = TenantContext.get().getTenantId();
        size = Math.min(size, 200);
        Pageable pageable = PageRequest.of(page, size);
        return repository.search(tenantId, userId, action, resourceType, from, to, pageable);
    }

    /**
     * List events for the current tenant paginated.
     */
    public Page<AuditEvent> list(int page, int size) {
        String tenantId = TenantContext.get().getTenantId();
        return repository.findByTenantIdOrderByOccurredAtDesc(
                tenantId, PageRequest.of(page, Math.min(size, 200)));
    }

    /**
     * Export audit events as a CSV file.
     *
     * @param userId       optional user filter
     * @param action       optional action filter
     * @param resourceType optional resource type filter
     * @param from         optional start date
     * @param to           optional end date
     * @return the file path to the exported CSV
     */
    public String exportCsv(String userId, String action, String resourceType,
                           Instant from, Instant to) throws IOException {
        String tenantId = TenantContext.get().getTenantId();
        Pageable pageable = PageRequest.of(0, maxExportRows);

        Page<AuditEvent> page = repository.search(
                tenantId, userId, action, resourceType, from, to, pageable);

        Path exportDir = Paths.get(System.getProperty("java.io.tmpdir"), "audit-exports");
        Files.createDirectories(exportDir);

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-").replace(".", "-");
        String filename = String.format("audit_export_%s_%s.csv", tenantId, timestamp);
        Path filePath = exportDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            // Header
            writer.write("id,tenant_id,user_id,session_id,action,resource_type,resource_id," +
                    "correlation_id,ip_address,severity,message,occurred_at,recorded_at");
            writer.newLine();

            for (AuditEvent event : page.getContent()) {
                writer.write(formatCsvRow(event));
                writer.newLine();
            }
        }

        log.info("Audit export written: tenant={}, rows={}, file={}",
                tenantId, page.getNumberOfElements(), filePath);
        return filePath.toString();
    }

    /**
     * Count audit events for the current tenant.
     */
    public long count() {
        String tenantId = TenantContext.get().getTenantId();
        return repository.count();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String formatCsvRow(AuditEvent event) {
        List<String> fields = Arrays.asList(
                nullSafe(event.getId()),
                nullSafe(event.getTenantId()),
                nullSafe(event.getUserId()),
                nullSafe(event.getSessionId()),
                nullSafe(event.getAction()),
                nullSafe(event.getResourceType()),
                nullSafe(event.getResourceId()),
                nullSafe(event.getCorrelationId()),
                nullSafe(event.getIpAddress()),
                nullSafe(event.getSeverity()),
                escapeCsv(nullSafe(event.getMessage())),
                event.getOccurredAt() != null ? event.getOccurredAt().toString() : "",
                event.getRecordedAt() != null ? event.getRecordedAt().toString() : ""
        );
        return String.join(",", fields);
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
