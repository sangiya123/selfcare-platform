package com.selfcare.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Product mapping document — maps external provider product IDs to canonical product IDs.
 * Used for cross-provider product reconciliation and unified catalog.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product_mapping_documents")
@CompoundIndex(name = "tenant_source_canonical",
               def = "{'tenantId': 1, 'sourceProvider': 1, 'sourceProductId': 1}",
               unique = true)
public class ProductMappingDocument {

    @Id
    private String id;

    private String tenantId;
    private String environment;

    /** External provider identifier (e.g. "dialog-bss", "hutch-ocp", "aia-policy") */
    @Indexed
    private String sourceProvider;

    /** Product ID in the external provider's system */
    @Indexed
    private String sourceProductId;

    /** Source product name/description */
    private String sourceProductName;

    /** Canonical product ID in selfcare unified catalog */
    @Indexed
    private String canonicalProductId;

    /** Canonical product name */
    private String canonicalProductName;

    /** Canonical product category */
    private String canonicalCategory;

    /** Mapping confidence: HIGH, MEDIUM, LOW, MANUAL */
    private String confidence;

    /** Mapping status: ACTIVE, PENDING_REVIEW, REJECTED, DEPRECATED */
    @Indexed
    private String status;

    /** Additional attributes from source */
    private Map<String, Object> sourceAttributes;

    /** Override attributes for canonical product */
    private Map<String, Object> canonicalOverrides;

    /** Audit */
    private String createdBy;
    private Instant createdAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String updatedBy;
    private Instant updatedAt;

    @Version
    private Long mongoVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductMappingDocument that = (ProductMappingDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}