package com.selfcare.dashboard.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dashboard layout document — the DB source of truth for which widgets a
 * tenant/profile should render and in what order.
 *
 * <p>This is the platform analogue of the legacy midend dashboard layout
 * (see Dialog midend {@code layoutConfig}). Authored in MongoDB (via the
 * selfcare Studio or the tenant seed) and resolved at runtime by
 * {@link DashboardLayoutService} with a short Redis TTL cache. Nothing is
 * hardcoded in the service.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "dashboard_layouts")
public class DashboardLayoutDocument {

    @Id
    private String id;

    /** The tenant this layout belongs to (e.g. {@code "dialog-lk"}). */
    private String tenantId;

    /** Experience profile key (e.g. {@code "PREPAID"}, {@code "POSTPAID"}, {@code "MBB"}, {@code "DTV"}). */
    private String profileKey;

    /** Whether this layout is currently active/published. */
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    /** Ordered widget IDs to execute on the dashboard. */
    private List<String> widgetOrder;

    /** Per-widget config overrides (ordering props, composition metadata). */
    private Map<String, Object> widgetConfig;

    private String environment;
    private String status;
    private Instant updatedAt;
}