package com.selfcare.dialog.provider;

import java.util.Map;

/**
 * Operator view of a resolved {@link DialogHttpClient.DialogConfig}.
 *
 * <p>Every operator-specific API shape — path templates, LOB values, response
 * field names, currency, enabled capabilities — is resolved from the tenant's
 * DB-backed integration configuration (editable in the Admin Portal under
 * Integrations), never hardcoded. Dialog is simply the operator that publishes
 * MIFE/BSS-shaped values; another operator using a different API configures
 * its own values against the same pack classes.</p>
 *
 * <p>Config keys ({@code metadata}/{@code fieldMapping} of the integration):</p>
 *
 * <ul>
 *   <li>{@code path.<resource>} — API path template; {@code {name}} placeholders
 *       are substituted with the arguments given to {@link #path(String, String, Map)}</li>
 *   <li>{@code lob} / {@code lob.<resource>} — line-of-business query value</li>
 *   <li>{@code field.<canonical>} or {@code fieldMapping} — wire field name for a
 *       canonical field</li>
 *   <li>{@code capabilities} — comma-separated list of enabled capabilities
 *       (when absent, everything is allowed)</li>
 * </ul>
 */
public record DialogOperator(DialogHttpClient.DialogConfig cfg) {

    private static final String DEFAULT_LOB = "GSM";
    private static final String DEFAULT_CURRENCY = "LKR";

    public boolean configured() {
        return cfg != null && cfg.isValid();
    }

    /**
     * Resolve an API path template for a resource.
     *
     * @param resource config key suffix: {@code path.<resource>}
     * @param fallback default template when not configured
     * @param vars     substitutions for {@code {name}} placeholders
     */
    public String path(String resource, String fallback, Map<String, Object> vars) {
        String template = cfg.meta("path." + resource, fallback);
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        String resolved = template;
        for (Map.Entry<String, Object> e : vars.entrySet()) {
            resolved = resolved.replace("{" + e.getKey() + "}",
                    e.getValue() != null ? e.getValue().toString() : "");
        }
        return resolved;
    }

    /**
     * Resolve the LOB (line of business) value — metadata {@code lob.<resource>},
     * else {@code lob}, else the supplied default.
     */
    public String lob(String resource, String fallback) {
        String byResource = cfg.meta("lob." + resource, null);
        if (byResource != null) return byResource;
        return cfg.meta("lob", fallback != null ? fallback : DEFAULT_LOB);
    }

    /**
     * Resolve the wire field name for a canonical field.
     */
    public String field(String canonical, String fallback) {
        return cfg.mapping(canonical, fallback);
    }

    /**
     * Resolve the currency default.
     */
    public String currency() {
        return cfg.mapping("currency", DEFAULT_CURRENCY);
    }

    /**
     * Whether the configured capabilities list contains a token.
     * When no capabilities are configured everything is allowed.
     */
    public boolean hasCapability(String token) {
        String caps = cfg.meta("capabilities", null);
        if (caps == null) return true;
        for (String c : caps.split(",")) {
            if (c != null && c.trim().equals(token)) return true;
        }
        return false;
    }
}