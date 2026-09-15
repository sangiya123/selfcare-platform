package com.selfcare.audit.scheduler;

import com.selfcare.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled maintenance for the audit service.
 *
 *  - Audit retention: enforces per-tenant or platform-wide retention policy
 *    by deleting events older than the configured retention window.
 *    Default: 2 years for compliance (GDPR-safe: anonymise PII rather than delete
 *    for active records, but old records are pruned).
 *
 *  - Prune exports: cleans up CSV export files older than 7 days in the temp directory.
 *
 * IMPORTANT: The audit table is append-only. This job is the ONLY mechanism for
 * deletion. Ensure retention policy is reviewed with your legal/compliance team.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    private final AuditEventRepository repository;

    @Value("${audit.retention.days:730}")
    private int retentionDays; // default: 2 years

    /**
     * Gate actual deletion behind a property. Off by default in dev/test; on
     * in production (set by the helm values). When off, the job logs the
     * count that would be pruned but does not delete.
     */
    @Value("${audit.retention.physical-delete-enabled:false}")
    private boolean physicalDeleteEnabled;

    /** Daily at 04:00 server time */
    @Scheduled(cron = "${audit.retention.cron:0 0 4 * * *}")
    public void enforceRetention() {
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            long beforeCount = repository.count();
            log.info("Audit retention check: cutoff={}, retentionDays={}, currentCount={}, physicalDeleteEnabled={}",
                    cutoff, retentionDays, beforeCount, physicalDeleteEnabled);
            if (physicalDeleteEnabled) {
                long deleted = repository.deleteByRecordedAtBefore(cutoff);
                long afterCount = repository.count();
                log.info("Audit retention applied: {} events older than {} days deleted ({} -> {})",
                        deleted, retentionDays, beforeCount, afterCount);
            } else {
                // Log-only mode: count records that would be deleted
                long eligible = repository.countByRecordedAtBefore(cutoff);
                log.info("Audit retention log-only: {} events would be deleted (retentionDays={}); set audit.retention.physical-delete-enabled=true to enable actual deletion",
                        eligible, retentionDays);
            }
        } catch (Exception e) {
            log.error("Audit retention job failed: {}", e.getMessage(), e);
        }
    }

    /** Weekly on Sunday at 05:00 */
    @Scheduled(cron = "${audit.export-cleanup-cron:0 0 5 * * SUN}")
    public void cleanupExports() {
        try {
            java.nio.file.Path exportDir = java.nio.file.Paths.get(
                    System.getProperty("java.io.tmpdir"), "audit-exports");
            if (!java.nio.file.Files.exists(exportDir)) return;

            Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
            int pruned = 0;
            try (var stream = java.nio.file.Files.list(exportDir)) {
                for (var file : stream.toList()) {
                    if (java.nio.file.Files.isRegularFile(file) &&
                            java.nio.file.Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                        java.nio.file.Files.deleteIfExists(file);
                        pruned++;
                    }
                }
            }
            if (pruned > 0) {
                log.info("Audit export cleanup: {} files older than 7 days removed", pruned);
            }
        } catch (Exception e) {
            log.error("Audit export cleanup failed: {}", e.getMessage(), e);
        }
    }
}
