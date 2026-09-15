package com.selfcare.reporting.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Declares a named parameter that the report's query accepts.
 *
 * <p>Parameters are substituted into the {@link QuerySpec#sqlTemplate}
 * using colon-prefixed names ({@code :tenantId}, {@code :fromDate}, etc.).</p>
 *
 * <p>Parameters of type {@code TENANT}, {@code ENVIRONMENT}, {@code CHANNEL},
 * {@code PROVIDER} may carry a predefined list of {@link #options}
 * to drive dropdown rendering in the UI.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParameterSpec {

    /** Unique name used as the placeholder in the SQL template (e.g. {@code "fromDate"}). */
    private String name;

    /** Human-readable label for the UI. */
    private String label;

    /**
     * Parameter type — drives validation, UI widget, and masking rules.
     */
    private ParamType type;

    /** Whether the caller must supply a value; defaults to {@code true}. */
    @Builder.Default
    private boolean required = true;

    /**
     * Default value used when the caller omits this parameter.
     * Examples: {@code "7"} for a default lookback of 7 days,
     * {@code "PREPAID"} for a default customer segment.
     */
    private String defaultValue;

    /**
     * Predefined options for enum-like parameters (TENANT, ENVIRONMENT, CHANNEL, PROVIDER, etc.).
     * When set, the UI renders a dropdown; when absent the field is free-text.
     */
    private List<String> options;

    // -------------------------------------------------------------------------
    // Nested type
    // -------------------------------------------------------------------------

    public enum ParamType {
        /** ISO-8601 date string ({@code yyyy-MM-dd}). */
        DATE,
        /** Tenant ID / operator identifier. */
        TENANT,
        /** Environment: {@code PRODUCTION}, {@code STAGING}, {@code DEVELOPMENT}. */
        ENVIRONMENT,
        /** Channel: {@code APP}, {@code WEB}, {@code USSD}, {@code SMS}, {@code IVR}. */
        CHANNEL,
        /** Downstream provider name: {@code ORANGE}, {@code SLT}, etc. */
        PROVIDER,
        /** Currency code: {@code LKR}, {@code USD}, etc. */
        CURRENCY,
        /** Free-form text. */
        STRING,
        /** Numeric value (integer or decimal). */
        NUMBER,
        /** Boolean flag: {@code true} / {@code false}. */
        BOOLEAN
    }
}
