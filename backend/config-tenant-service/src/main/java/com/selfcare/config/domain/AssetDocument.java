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
 * Asset metadata document stored in MongoDB.
 * Binary files are stored in object storage (S3/MinIO); this document holds metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "asset_documents")
@CompoundIndex(name = "tenant_type_status",
               def = "{'tenantId': 1, 'type': 1, 'status': 1}")
public class AssetDocument {

    @Id
    private String id;

    private String tenantId;
    private String environment;

    /** Asset type: IMAGE, ICON, LOGO, BANNER, DOCUMENT, OTHER */
    @Indexed
    private String type;

    /** Original filename */
    private String filename;

    /** MIME type */
    private String mimeType;

    /** File size in bytes */
    private long sizeBytes;

    /** Storage URL (CDN or object storage) */
    private String url;

    /** Storage bucket/path reference */
    private String storageKey;

    /** Width (for images) */
    private Integer width;

    /** Height (for images) */
    private Integer height;

    /** Alt text for accessibility */
    private String altText;

    /** Tags for search/filtering */
    private List<String> tags;

    /** Custom metadata */
    private Map<String, Object> metadata;

    /** Status: ACTIVE, ARCHIVED, DELETED */
    @Indexed
    private String status;

    /** Audit */
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;

    @Version
    private Long mongoVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssetDocument that = (AssetDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}