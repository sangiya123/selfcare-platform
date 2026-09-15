package com.selfcare.dashboard.service;

import reactor.core.publisher.Mono;

/**
 * Interface for dashboard widget providers.
 *
 * Each widget on the dashboard implements this interface.
 * Widgets are discovered via Spring component scanning and registered
 * by widget ID.
 *
 * Widget providers are stateless and thread-safe.
 */
public interface WidgetProvider {

    /**
     * Unique widget identifier.
     */
    String getWidgetId();

    /**
     * Display name for the widget.
     */
    String getDisplayName();

    /**
     * Check if this widget is available for the given user context.
     */
    boolean isAvailable(String tenantId, String connectionId, String profileKey);

    /**
     * Execute the widget and return its data.
     *
     * @param connectionId The active connection ID
     * @param tenantId The tenant identifier
     * @param profileKey The resolved profile key
     * @return Widget data as a Map, or an error Mono
     */
    Mono<Object> execute(String connectionId, String tenantId, String profileKey);

    /**
     * Timeout for this widget in milliseconds.
     * Return null to use the default.
     */
    default Long getTimeoutMs() {
        return null;
    }

    /**
     * Data freshness classification.
     */
    default DataFreshness getFreshness() {
        return DataFreshness.REAL_TIME;
    }

    enum DataFreshness {
        REAL_TIME,        // Live data from source
        NEAR_REAL_TIME,   // Cached/read-model data
        REFERENCE,        // Static/reference data
        OPTIONAL          // Can be skipped under load
    }
}