package com.selfcare.reporting.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Canonical report definition in the governed catalog.
 *
 * <p>Each entry describes:
 * <ul>
 *   <li>Identity: id (URL-safe slug), name, description.</li>
 *   <li>Classification: category and required permission.</li>
 *   <li>Query: the {@link QuerySpec} — SQL template, parameters, output columns.</li>
 *   <li>Operations: default schedule, freshness SLA, estimated size.</li>
 *   <li>AI readiness: whether natural-language queries are permitted against this report.</li>
 * </ul>
 *
 * <p>Report definitions are immutable at runtime (they live in code, not the database).
 * The JPA entity {@link com.selfcare.reporting.domain.ReportDefinition} wraps a
 * subset of these fields for per-tenant customisation (schedule, recipients, etc.).</p>
 *
 * @see QuerySpec
 * @see ColumnSpec
 * @see ParameterSpec
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDefinition {

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    /**
     * URL-safe, globally unique identifier.
     * Examples: {@code "active-subscribers-dau"}, {@code "payment-attempts-success-failure"}.
     */
    private String id;

    /** Human-readable display name. */
    private String name;

    /** One-paragraph description of what the report shows. */
    private String description;

    // -------------------------------------------------------------------------
    // Classification
    // -------------------------------------------------------------------------

    /** Top-level grouping for browsing the catalog. */
    private ReportCategory category;

    /**
     * Spring Security authority required to execute this report.
     * Examples: {@code "REPORT_ANALYST"}, {@code "ROLE_ADMIN"}.
     */
    private String requiredPermission;

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /** Query template and parameter schema. */
    private QuerySpec query;

    /** Default filter values applied when the caller provides no parameters. */
    @Builder.Default
    private Map<String, Object> defaultFilters = Map.of();

    /** Ordered list of output columns. Drives CSV headers and JSON field names. */
    private List<ColumnSpec> outputColumns;

    /**
     * Semantic label map for AI / LLM consumption.
     * Keys are the column keys; values are semantic tags
     * (e.g. {@code "amount_lkr"}, {@code "msisdn_masked"}, {@code "arpu_usd"}).
     *
     * <p>When an AI translates a natural-language query to a governed metric,
     * it must use only semantic labels defined in this map.</p>
     */
    @Builder.Default
    private Map<String, String> semanticLabels = Map.of();

    // -------------------------------------------------------------------------
    // Scheduling & operations
    // -------------------------------------------------------------------------

    /**
     * Cron expression for default schedule (e.g. {@code "0 0 2 * * ?"} for 02:00 daily).
     * {@code null} means ad-hoc only.
     */
    private String defaultSchedule;

    /**
     * Owner team / metric owner email address.
     * Shown in the catalog UI so consumers know who to contact about definitions.
     */
    private String owner;

    /**
     * Data freshness SLA in minutes.
     * Indicates how stale the data may be (e.g. 60 = data is at most 1 hour old).
     */
    private int freshnessSlaMinutes;

    /** List of physical table names used by the query. Used for data lineage. */
    private List<String> dataSourceTables;

    /**
     * Estimated row count per execution.
     * Used for async-queue sizing and user expectation-setting.
     */
    private long estimatedRowsPerRun;

    /**
     * Estimated execution duration in seconds.
     */
    private int estimatedDurationSeconds;

    // -------------------------------------------------------------------------
    // AI readiness
    // -------------------------------------------------------------------------

    /**
     * When {@code true}, this report may be queried via natural language
     * through the AI reporting assistant. The AI will translate the user's
     * question using the {@link #semanticLabels} map and present the
     * interpreted query for confirmation before execution.
     */
    @Builder.Default
    private boolean isAiAssisted = false;
}
