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
import java.util.Map;

/**
 * Theme configuration document stored in MongoDB.
 *
 * Design tokens (colors, typography, spacing, etc.) per tenant.
 * Operator overrides the selfcare base theme.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "theme_documents")
@CompoundIndex(name = "tenant_name_version",
               def = "{'tenantId': 1, 'name': 1, 'version': 1}",
               unique = true)
public class ThemeDocument {

    @Id
    private String id;

    private String tenantId;
    private String environment;

    /** Theme name, e.g., "dialog-default", "hutch-purple" */
    @Indexed
    private String name;

    /** Semantic version: 1.0.0, 2.0.0 */
    private String version;

    /** Version number for ordering */
    private int versionNumber;

    /** Status: DRAFT, PUBLISHED, ARCHIVED */
    @Indexed
    private String status;

    /** selfcare base palette tokens */
    private ThemeTokens baseTokens;

    /** Operator override tokens (may carry a darkMode layer) */
    private ThemeTokens overrideTokens;

    /** Computed final tokens (base merged with override) */
    private ThemeTokens resolvedTokens;

    /** Operator visual identity — surfaced to the compiled manifest as logoUrl. */
    private Branding branding;

    /** Audit */
    private String publishedBy;
    private Instant publishedAt;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long mongoVersion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThemeTokens {
        // Color tokens
        private Map<String, String> colors;
        // Typography tokens
        private Map<String, Object> typography;
        // Spacing tokens (in px)
        private Map<String, Integer> spacing;
        // Border radius tokens (in px)
        private Map<String, Integer> borderRadius;
        // Elevation/shadow tokens
        private Map<String, Object> elevation;
        // Icon size tokens
        private Map<String, Integer> iconSizes;
        // Breakpoints
        private Map<String, Integer> breakpoints;
        // Button styles
        private Map<String, Object> buttons;
        // Card styles
        private Map<String, Object> cards;
        // Dark-mode override layer (design tokens for dark mode), authored inside
        // the operator override block and compiled to the manifest `dark` layer.
        private ThemeTokens darkMode;
    }

    /** Operator brand assets (logo, app icon, favicon, splash, login logo). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Branding {
        private BrandAsset logo;
        private BrandAsset appIcon;
        private BrandAsset favicon;
        private BrandAsset splash;
        private BrandAsset loginLogo;
    }

    /** A single brand asset reference (CMS asset + CDN URL). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandAsset {
        private String assetId;
        private String url;
        private String alt;
        private Integer width;
        private Integer height;
    }
}