package com.selfcare.reporting.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a single column in a report's output.
 *
 * <p>Each column carries a semantic label that AI can reference
 * (e.g. {@code "revenue_lkr"}, {@code "msisdn_masked"}) so that
 * natural-language queries can be translated to governed queries.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnSpec {

    /**
     * Column key used in the query result / CSV header / JSON field name.
     * Must be lowercase-with-hyphens (e.g. {@code "tenant_id"}, {@code "amount_lkr"}).
     */
    private String key;

    /** Human-readable label shown in the UI column header. */
    private String label;

    /**
     * Logical data type — drives formatting and masking rules.
     */
    private ColumnType type;

    /**
     * Aggregation applied by the reporting engine (not the raw SQL).
     * {@code NONE} means the column is a dimension or a pre-aggregated value.
     */
    @Builder.Default
    private Aggregation aggregation = Aggregation.NONE;

    /**
     * Optional semantic label for AI/LLM consumption.
     * Examples: {@code "revenue_lkr"}, {@code "msisdn_masked"},
     * {@code "arpu_usd"}, {@code "churn_score"}, {@code "latency_ms"}.
     */
    private String semanticLabel;

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    public enum ColumnType {
        STRING,
        NUMBER,
        DATE,
        DATETIME,
        CURRENCY,
        PERCENT,
        BOOLEAN,
        /** MSISDN with last 4 digits only (e.g. **** **** **** 1234) */
        MSISDN_MASKED,
        /** Email with domain only (e.g. ****@example.com) */
        EMAIL_MASKED
    }

    public enum Aggregation {
        NONE,
        SUM,
        AVG,
        COUNT,
        COUNT_DISTINCT,
        MIN,
        MAX
    }
}
