package com.omobio.config.domain;

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

/**
 * Layout configuration document stored in MongoDB.
 *
 * Represents a page/experience layout for a specific tenant, environment,
 * LOB, customer type, and segment.
 *
 * Document model (generalized from Dialog's existing Mongo layout structure):
 *
 * {
 *   "_id": ObjectId,
 *   "tenantId": "dialog-lk",
 *   "environment": "prod",
 *   "experience": "home",
 *   "profileKey": "mobile_prepaid_youth",
 *   "schemaVersion": "2.0",
 *   "configVersion": 184,
 *   "compatibility": {
 *     "platforms": ["ios", "android", "huawei"],
 *     "minAppVersion": "20.0.0"
 *   },
 *   "themeRef": "dialog-default@17",
 *   "navigationRef": "dialog-main@9",
 *   "sections": [
 *     {
 *       "id": "usage",
 *       "component": "UsageSummary",
 *       "variant": "hero",
 *       "dataSource": "usage.summary",
 *       "props": {"showHistory": true},
 *       "visibleWhen": {"feature": "usage.enabled"},
 *       "actions": [{"event":"history","type":"NAVIGATE","route":"/usage/history"}],
 *       "states": {"timeout":"inlineRetry","unavailable":"hide"},
 *       "analytics": {"impression":"home_usage_impression"}
 *     }
 *   ],
 *   "status": "PUBLISHED",
 *   "publishedBy": "admin@dialog.lk",
 *   "publishedAt": "2026-09-01T10:00:00Z",
 *   "createdAt": "...",
 *   "updatedAt": "...",
 *   "createdBy": "...",
 *   "version": 1
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "layout_documents")
@CompoundIndex(name = "tenant_env_experience_profile",
               def = "{'tenantId': 1, 'environment': 1, 'experience': 1, 'profileKey': 1}",
               unique = true)
public class LayoutDocument {

    @Id
    private String id;

    /** Tenant identifier (e.g., "dialog-lk", "hutch-lk") */
    @Indexed
    private String tenantId;

    /** Environment: dev, qa, staging, prod */
    @Indexed
    private String environment;

    /** Experience name: home, packages, bills, payments, support, profile, etc. */
    @Indexed
    private String experience;

    /**
     * Profile key for matching: LOB + customer type + segment.
     * e.g., "mobile_prepaid_youth", "bb_postpaid_enterprise", "dtv_prepaid_family"
     */
    @Indexed
    private String profileKey;

    /** Config schema version (e.g., "2.0") */
    private String schemaVersion;

    /** Auto-incremented version per publish */
    private int configVersion;

    /** Client compatibility constraints */
    private Compatibility compatibility;

    /** Reference to theme document */
    private String themeRef;

    /** Reference to navigation document */
    private String navigationRef;

    /** Ordered list of layout sections */
    private List<Section> sections;

    /** Document status: DRAFT, REVIEW, APPROVED, PUBLISHED, ARCHIVED */
    @Indexed
    private String status;

    /** Audit fields */
    private String publishedBy;
    private Instant publishedAt;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    /** MongoDB optimistic locking version */
    @Version
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Compatibility {
        private List<String> platforms; // android, ios, huawei, web
        private String minAppVersion;
        private String maxAppVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String id;
        private String component;       // Component registry ID
        private String variant;         // Component variant
        private String dataSource;      // Data binding contract
        private Map<String, Object> props;           // Component props
        private VisibilityCondition visibleWhen;      // Visibility rules
        private List<Action> actions;   // Action definitions
        private Map<String, String> states; // timeout, unavailable, error states
        private Map<String, String> analytics; // analytics event IDs
        private Integer order;           // Section ordering within page
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisibilityCondition {
        private String feature;         // Feature flag name
        private String lob;             // MOBILE, BB, DTV, FIBRE
        private String connectionType;   // prepaid, postpaid
        private String segment;          // youth, enterprise, family
        private Map<String, Object> custom; // Custom condition expressions
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private String event;           // UI event that triggers action
        private String type;            // NAVIGATE, START_JOURNEY, CALL_API, etc.
        private String route;           // Route for NAVIGATE
        private String journeyId;        // Journey ID for START_JOURNEY
        private Map<String, Object> params; // Action parameters
        private String confirmation;    // none, policy, explicit
        private String analyticsEvent;  // Analytics event name
    }
}