package com.selfcare.platform.common.contract.experience;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical experience (dashboard / selfcare surface) contract — ADR-008 partial-response aware.
 * Industry-neutral: widgets are configured, not fixed.
 */
public final class ExperienceContract {

    private ExperienceContract() {}

    public record ExperienceRequest(
            String tenantId,
            String userId,
            String connectionId,
            List<String> widgetIds,
            Map<String, String> params) {
    }

    public record LayoutConfig(
            String layoutId,
            String version,
            List<DashboardWidget> widgets,
            int deadlineMillis) {
    }

    public record DashboardWidget(
            String widgetId,
            String type,
            String title,
            String dataRef,
            int order,
            int timeoutMillis,
            int cacheTtlSeconds,
            Map<String, Object> fallback) {
    }

    public record WidgetLoadResult(
            String widgetId,
            WidgetStatus status,
            Object data,
            String errorCode,
            boolean fallbackUsed,
            long elapsedMillis) {
    }

    public record PartialDashboardResponse(
            List<WidgetLoadResult> widgets,
            Instant deadlineAt,
            boolean partial) {
    }

    public enum WidgetStatus {
        SUCCESS, FALLBACK, TIMEOUT, ERROR, DEGRADED
    }

    public interface ExperienceService {
        PartialDashboardResponse getDashboard(ExperienceRequest request);

        LayoutConfig getLayout(String tenantId, String layoutId);
    }
}