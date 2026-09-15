package com.selfcare.reporting.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Encapsulates everything the reporting engine needs to execute a report.
 *
 * <p>Currently the primary execution path is ANSI-SQL over the MySQL data warehouse,
 * but the {@link QueryType} enum leaves room for other engines (Kafka aggregate topics,
 * MongoDB aggregation pipelines, Redis counters, AI-derived metrics).</p>
 *
 * <h3>SQL template conventions</h3>
 * <ul>
 *   <li>Always filter by {@code :tenantId} to enforce tenant isolation.</li>
 *   <li>Use {@code :fromDate} / {@code :toDate} for date-range filters.</li>
 *   <li>Additional named parameters follow the same colon syntax.</li>
 *   <li>All table names in {@code FROM} / {@code JOIN} must be listed in
 *       {@code dataSourceTables} on the parent {@link ReportDefinition}.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * SELECT date(created_at)      AS report_date,
 *        tenant_id,
 *        COUNT(*)              AS total_users
 * FROM   user_sessions
 * WHERE  tenant_id  = :tenantId
 *   AND  created_at >= :fromDate
 *   AND  created_at <  :toDate
 * GROUP  BY 1, 2
 * ORDER  BY 1, 2
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySpec {

    /**
     * Execution engine / data-source type.
     */
    private QueryType type;

    /**
     * Parameterised query / pipeline template.
     * Placeholders use colon-prefix (e.g. {@code :tenantId}, {@code :fromDate}).
     */
    private String sqlTemplate;

    /**
     * Ordered list of parameter declarations that this query accepts.
     * The UI renders these as filter controls.
     */
    private List<ParameterSpec> parameters;

    // -------------------------------------------------------------------------
    // Nested type
    // -------------------------------------------------------------------------

    public enum QueryType {
        /**
         * Standard ANSI-SQL SELECT executed against the MySQL read replica / data warehouse.
         * Parameters are substituted before JDBC execution.
         */
        SQL,
        /**
         * Kafka consumer-group aggregate table (materialised view refreshed on a schedule).
         * The SQL template is a simple SELECT from the pre-aggregated Kafka topic table.
         */
        KAFKA_AGGREGATE,
        /**
         * MongoDB aggregation pipeline JSON (executed via MongoTemplate).
         * The SQL template field holds the JSON pipeline string.
         */
        MONGO_AGGREGATE,
        /**
         * A Redis key pattern (e.g. {@code hgetall daily:dau:2024-01-15}) resolved at runtime.
         */
        REDIS_COUNTER,
        /**
         * Metric derived entirely by an AI model from other report outputs.
         * The SQL template field may be empty; the AI service resolves the derivation.
         */
        AI_DERIVED
    }
}
