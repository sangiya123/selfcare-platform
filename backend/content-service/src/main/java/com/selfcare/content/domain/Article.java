package com.selfcare.content.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Article — long-form content (news, how-tos, promotions).
 *
 * Stored in MongoDB to support:
 * - Flexible content model (blocks, sections, media)
 * - Multi-locale via translations map
 * - Versioning via version field
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "articles")
public class Article {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    /** Slug for URL: /content/article/{slug} */
    @Indexed
    private String slug;

    private String category; // NEWS, HOW_TO, PROMOTION, ANNOUNCEMENT

    /** Default locale content */
    private String defaultLocale;

    /** Translations: { "en": {...}, "si": {...} } */
    private Map<String, Translation> translations;

    /** Tags for search */
    private List<String> tags;

    /** Featured image URL */
    private String featuredImageUrl;

    /** Author */
    private String author;

    /** Status: DRAFT, REVIEW, PUBLISHED, ARCHIVED */
    @Indexed
    private String status;

    /** Visibility: ALL, POSTPAID_ONLY, PREPAID_ONLY, SEGMENT_X */
    private String visibility;

    /** Version for content updates */
    private Integer version;

    /** Scheduled publish time (null = immediate) */
    private Instant publishAt;

    /** Unpublish time */
    private Instant unpublishAt;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Translation {
        private String title;
        private String summary;
        private String body; // HTML / Markdown
        private String metaDescription;
        private String featuredImageUrl;
    }
}