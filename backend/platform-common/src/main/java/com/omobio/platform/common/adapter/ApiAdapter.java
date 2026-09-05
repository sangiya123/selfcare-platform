package com.omobio.platform.common.adapter;

/**
 * Marker interface for all operator-specific adapters.
 *
 * Every external system integration (OSS/BSS, legacy PHP, SOAP, REST, etc.)
 * implements one or more of these adapters to expose canonical capabilities.
 *
 * Example adapters:
 *   BalanceProvider extends ApiAdapter
 *   PaymentProvider extends ApiAdapter
 *   UsageProvider extends ApiAdapter
 *   ProductCatalogProvider extends ApiAdapter
 *
 * Services use ApiAdapterRegistry<T> to get the correct adapter for a tenant.
 *
 * @see ApiAdapterRegistry
 * @see ApiAdapterRegistry.RegisterAdapter
 */
public interface ApiAdapter {

    /**
     * Returns the unique identifier for this adapter implementation.
     * Usually matches the operator ID (e.g., "dialog-lk", "hutch-lk").
     */
    String getAdapterId();

    /**
     * Returns true if this adapter is healthy and ready to serve requests.
     * Called periodically by health checks.
     */
    default boolean isHealthy() {
        return true;
    }

    /**
     * Optional: cleanup resources when adapter is deregistered.
     * Called during shutdown or when adapter is replaced.
     */
    default void destroy() {
        // No-op by default
    }
}