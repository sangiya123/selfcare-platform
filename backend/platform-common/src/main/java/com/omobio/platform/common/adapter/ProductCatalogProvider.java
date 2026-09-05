package com.omobio.platform.common.adapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Canonical interface for product / offer catalog lookups.
 * Cross-industry. Returns the canonical platform model regardless of
 * how the upstream BSS / CMS stores products.
 *
 * @see com.omobio.product.service.ProductCatalogService
 */
public interface ProductCatalogProvider extends ApiAdapter {

    // ─── Browse ─────────────────────────────────────────────────────────

    /**
     * List all products (active and inactive) for a tenant.
     * Use {@code getActiveProducts} for the consumer-facing catalog.
     *
     * @param tenantId    the tenant
     * @param category    filter by category (may be null)
     * @param page        page number (0-indexed)
     * @param pageSize    results per page (max 100)
     * @return            paginated product list
     */
    ProductListResult listProducts(
            String tenantId,
            String category,
            int page,
            int pageSize
    );

    /**
     * List only active, visible products for a tenant.
     *
     * @param tenantId   the tenant
     * @param category   filter by category
     * @param segment    filter by customer segment: {@code consumer}, {@code smb}, {@code enterprise}
     * @return           list of active products
     */
    List<ProductSummary> getActiveProducts(
            String tenantId,
            String category,
            String segment
    );

    // ─── Detail ─────────────────────────────────────────────────────────

    /**
     * Get the full product details by ID.
     *
     * @param tenantId    the tenant
     * @param productId   product identifier
     * @param fields      subset of fields to return (empty = all)
     * @return            full product or throws if not found
     */
    ProductDetail getProduct(
            String tenantId,
            String productId,
            List<String> fields
    );

    // ─── Recommendations ────────────────────────────────────────────────

    /**
     * Get personalised product recommendations for a connection.
     *
     * @param tenantId     the tenant
     * @param connectionId the customer's connection
     * @param limit        max recommendations
     * @param context      browsing context / current page (may be null)
     * @return             ranked list of product IDs with reasons
     */
    List<RecommendedProduct> getRecommendations(
            String tenantId,
            String connectionId,
            int limit,
            String context
    );

    // ─── Inner types ───────────────────────────────────────────────────

    record ProductListResult(
            List<ProductSummary> products,
            int page,
            int pageSize,
            int totalCount,
            int totalPages
    ) {}

    record ProductSummary(
            String productId,
            String name,
            String category,
            String segment,
            String description,
            String thumbnailUrl,
            boolean isActive,
            String effectivePrice
    ) {}

    record ProductDetail(
            String productId,
            String name,
            String description,
            String category,
            String segment,
            String priceType,    // flat | recurring | usage-based
            List<ProductPrice> prices,
            List<String> includedAllowances,
            List<String> terms,
            List<String> excludedCountries,
            Map<String, String> attributes,  // arbitrary key-value metadata
            boolean isActive,
            long validFrom,
            long validTo
    ) {}

    record ProductPrice(
            String priceId,
            BigDecimal amount,
            String currency,
            String billingCycle,  // one-time | daily | weekly | monthly | annual
            String taxCategory
    ) {}

    record RecommendedProduct(
            String productId,
            String name,
            double score,
            String reason,         // e.g. "compatible with your current plan"
            List<String> tags
    ) {}
}
