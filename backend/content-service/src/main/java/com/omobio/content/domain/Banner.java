package com.omobio.content.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Banner — promotional or informational banner (hero, carousel, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "banners")
public class Banner {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    /** Position: HERO_TOP, CAROUSEL, SIDEBAR, FOOTER */
    @Indexed
    private String position;

    private Map<String, Translation> translations;

    /** Image URL */
    private String imageUrl;

    /** Mobile image (different aspect ratio) */
    private String mobileImageUrl;

    /** CTA link target */
    private String targetUrl;

    /** CTA type: DEEP_LINK, EXTERNAL_URL, INTERNAL_PAGE */
    private String targetType;

    /** CTA text */
    private String ctaText;

    /** Display order */
    private Integer displayOrder;

    /** Active window */
    private Instant activeFrom;
    private Instant activeTo;

    private String status; // ACTIVE, INACTIVE

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Translation {
        private String title;
        private String subtitle;
        private String description;
    }
}