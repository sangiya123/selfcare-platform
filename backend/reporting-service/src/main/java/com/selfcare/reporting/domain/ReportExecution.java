package com.selfcare.reporting.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * A single report execution record.
 *
 * Tracks each time a {@link ReportDefinition} is run.
 * Stores status, start/end times, parameters used, and a link to the result.
 *
 * @see ReportDefinition
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_executions", indexes = {
    @Index(name = "ix_exec_tenant", columnList = "tenant_id"),
    @Index(name = "ix_exec_report", columnList = "report_id"),
    @Index(name = "ix_exec_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class ReportExecution {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "report_id", nullable = false, length = 64)
    private String reportId;

    /**
     * Lifecycle status.
     * - PENDING: queued, not yet started
     * - RUNNING: currently executing
     * - COMPLETED: finished successfully
     * - FAILED: error during execution
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Parameters used for this execution (JSON).
     */
    @Lob
    @Column(name = "parameters_json")
    private String parameters;

    /** Number of rows in the result. */
    @Column(name = "row_count")
    private Long rowCount;

    /**
     * URL to the result artifact (CSV/JSON/EXCEL/PDF).
     * Null while execution is pending/running.
     */
    @Column(name = "result_url", length = 1024)
    private String resultUrl;

    /** File size in bytes. */
    @Column(name = "result_size_bytes")
    private Long resultSizeBytes;

    /** Error message if the execution failed. */
    @Column(name = "error", length = 2048)
    private String error;

    /** Admin user who triggered this execution (or "scheduler" for scheduled). */
    @Column(name = "triggered_by", length = 128)
    private String triggeredBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
