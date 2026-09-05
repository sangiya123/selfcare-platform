package com.omobio.reporting.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A report definition — a reusable template for a report.
 *
 * Definitions describe the SQL/query template, parameters, output format,
 * optional schedule, and recipients. Per-tenant.
 *
 * <p>Lifecycle: DRAFT -> ACTIVE -> DISABLED</p>
 *
 * @see ReportExecution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "report_definitions", indexes = {
    @Index(name = "ix_report_def_tenant", columnList = "tenant_id"),
    @Index(name = "ix_report_def_tenant_name", columnList = "tenant_id, name", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class ReportDefinition {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /** Human-readable name; unique per tenant. */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** Detailed description. */
    @Column(name = "description", length = 512)
    private String description;

    /**
     * Query template — SQL (SELECT) or template string with parameter placeholders.
     * The renderer substitutes parameters before execution.
     */
    @Lob
    @Column(name = "query", nullable = false)
    private String query;

    /**
     * Parameter schema — names, types, defaults.
     * Stored as JSON-serialized map.
     */
    @Lob
    @Column(name = "parameters_json")
    private String parameters;

    /**
     * Optional cron expression for scheduled reports.
     * Null means ad-hoc only.
     */
    @Column(name = "schedule_cron", length = 64)
    private String schedule;

    /** Comma-separated list of email addresses to send completed reports to. */
    @Column(name = "recipients", length = 1024)
    private String recipients;

    /**
     * Output format.
     * - PDF (stub - produces CSV with PDF extension marker; real PDF out of scope)
     * - CSV
     * - EXCEL
     * - JSON
     */
    @Column(name = "output_format", nullable = false, length = 16)
    private String outputFormat;

    /**
     * Status: DRAFT, ACTIVE, DISABLED.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Admin user who created the definition. */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /** Admin user who last updated the definition. */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Status {
        DRAFT,
        ACTIVE,
        DISABLED
    }

    public enum OutputFormat {
        PDF,
        CSV,
        EXCEL,
        JSON
    }
}
