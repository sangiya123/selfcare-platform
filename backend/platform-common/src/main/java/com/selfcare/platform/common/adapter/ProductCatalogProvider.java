package com.selfcare.platform.common.adapter;

import java.util.List;

/**
 * Provider interface for product catalog data.
 *
 * <p>Canonical home of the {@code ProductCatalogProvider} contract so telco
 * industry packs (Dialog, Hutch, Airtel) can implement it without depending
 * on a service module.</p>
 *
 * <p>Each operator implements this to expose their catalog in canonical form.
 * ProductService aggregates, dedups, and materializes into the read model.</p>
 */
public interface ProductCatalogProvider extends ApiAdapter {

    /**
     * Fetch all products from the operator's source system.
     * Used by the materialization scheduler.
     */
    List<RawProduct> fetchAllProducts();

    /**
     * Fetch a single product by source ID.
     */
    RawProduct fetchProduct(String sourceProductId);

    /**
     * Source priority (lower number = higher priority for dedup).
     */
    default int getSourcePriority() {
        return 100;
    }

    /**
     * Source identifier (e.g., "dialog-mife", "hutch-bss").
     */
    String getSourceSystem();

    /**
     * Raw product from operator source system.
     * Normalized to canonical form by the materialization pipeline.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class RawProduct {
        private String sourceProductId;
        private String name;
        private String description;
        private String category;
        private String subcategory;
        private String lob;
        private String connectionType;
        private java.math.BigDecimal price;
        private String currency;
        private Integer validityDays;
        private String allowances;  // JSON
        private String terms;
        private String imageUrl;
        private String badge;
        private Integer displayOrder;
        private String status;
        private java.util.List<String> tags;
        private java.time.Instant lastModified;
    }
}