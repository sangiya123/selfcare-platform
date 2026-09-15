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
 * Navigation configuration document stored in MongoDB.
 * Defines the complete navigation graph for a tenant's mobile app.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "navigation_documents")
@CompoundIndex(name = "tenant_name_version_status",
               def = "{'tenantId': 1, 'name': 1, 'version': 1, 'status': 1}",
               unique = true)
public class NavigationDocument {

    @Id
    private String id;

    private String tenantId;
    private String environment;

    /** Navigation document name, e.g. "default", "enterprise-v1" */
    @Indexed
    private String name;

    /** Semantic version: 1, 2, 3 */
    private int version;

    /** Status: DRAFT, REVIEW, APPROVED, PUBLISHED, ARCHIVED */
    @Indexed
    private String status;

    /** Bottom tab bar configuration */
    private List<Tab> tabs;

    /** Drawer/side menu items */
    private List<DrawerItem> drawerItems;

    /** Quick actions (floating action buttons, etc.) */
    private List<QuickAction> quickActions;

    /** Canonical in-app route table (path -> screen/component) */
    private List<Route> routes;

    /** HTTPS universal links that map to in-app routes */
    private UniversalLinksConfig universalLinks;

    /** Custom-scheme deep link mappings */
    private List<DeepLink> deepLinks;

    /** Allowed external domains for navigation */
    private List<String> allowedDomains;

    /** Metadata */
    private Map<String, Object> metadata;

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
    public static class Tab {
        private String id;
        private String label;
        private String icon;
        private String route;
        private Badge badge;
        private VisibleWhen visibleWhen;
        private int order;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Badge {
            private String source;
            private Integer max;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VisibleWhen {
            private String feature;
            private String lob;
            private String segment;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrawerItem {
        private String id;
        private String label;
        private String icon;
        private String route;
        private String section;
        private boolean requiresAuth = true;
        private Map<String, Object> visibleWhen;
        private int order;
        private boolean enabled = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickAction {
        private String id;
        private String label;
        private String icon;
        private NavAction action;
        private Map<String, Object> visibleWhen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NavAction {
        private String type; // NAVIGATE, OPEN_WEB, OPEN_WEB_SSO, START_JOURNEY, DEEP_LINK, MODAL, EXTERNAL_BROWSER
        private String route;
        private String url;
        private String journeyId;
        private Map<String, Object> params;
        private String analyticsEvent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Route {
        private String id;
        private String path;
        private String screen;
        private List<RouteParam> params;
        private boolean requiresAuth;
        private List<String> guards;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RouteParam {
            private String name;
            private String type; // string, number, boolean
            private boolean required;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversalLinksConfig {
        private Boolean enabled;
        private List<String> associatedDomains;
        private List<UniversalLink> universalLinks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversalLink {
        private String id;
        private String host;
        private String pathPattern;
        private String route;
        private Map<String, String> paramMap;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeepLink {
        private String id;
        private String scheme;
        private String pathPattern;
        private String route;
        private Map<String, String> paramMap;
        private String fallbackUrl;
        private boolean enabled = true;
        private String description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NavigationDocument that = (NavigationDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}