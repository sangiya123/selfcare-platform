package com.selfcare.config.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Feature flag document stored in MongoDB.
 *
 * Feature flags control runtime behaviour without code deployment.
 * The rollout percentage determines what fraction of users receive the feature.
 *
 * {
 *   "_id": ObjectId,
 *   "name": "dark_mode",
 *   "tenantId": "dialog-lk",
 *   "description": "Enable dark mode UI theme",
 *   "rolloutPercentage": 0-100,
 *   "targetUsers": { "segments": ["youth", "enterprise"], "lobs": ["MOBILE"] },
 *   "enabledEnvironments": ["prod", "staging"],
 *   "metadata": { "owner": "team-ux", "ticket": "JIRA-123" },
 *   "createdAt": "...",
 *   "updatedAt": "...",
 *   "createdBy": "...",
 *   "version": 1
 * }
 *
 * The flag name is unique per tenant.
 *
 * The flag is evaluated by the config SDK on the client side, and optionally
 * by the feature flag service (for server-side evaluation via Unleash).
 *
 * ADR-009: Config cannot execute arbitrary code. Feature flags are pure
 * configuration — the actual behaviour change is implemented in the component
 * or service code and gated by the flag value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "feature_flags")
@CompoundIndex(name = "tenant_name", def = "{'tenantId': 1, 'name': 1}", unique = true)
public class FeatureFlag {

    @Id
    private String id;

    /** Unique flag name within a tenant (e.g., "dark_mode", "ai_copilot"). */
    private String name;

    /** Tenant identifier (e.g., "dialog-lk", "aia-lk"). */
    private String tenantId;

    /** Human-readable description. */
    private String description;

    /**
     * Rollout percentage: 0 = off, 1-99 = staged rollout, 100 = all users.
     * A change from any value to 100% requires four-eyes approval
     * (FEATURE_ENABLE_100 action).
     */
    private int rolloutPercentage;

    /** Which user segments or LOBs this flag applies to. */
    private TargetUsers targetUsers;

    /** Which environments this flag is active in (dev, staging, prod). */
    private java.util.List<String> enabledEnvironments;

    /** Free-form metadata (owner, ticket, etc.). */
    private Map<String, Object> metadata;

    /** Audit fields. */
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    /** MongoDB optimistic locking. */
    @Version
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetUsers {
        private java.util.List<String> segments; // e.g., youth, enterprise
        private java.util.List<String> lobs;     // e.g., MOBILE, BB, DTV
        private java.util.List<String> userIds;  // explicit user allowlist
    }
}
