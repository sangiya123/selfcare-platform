package com.selfcare.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Operations AI Copilot — implements AI scope section 6.
 *
 * Capabilities:
 *  - incident copilot using logs/metrics/traces/runbooks
 *  - dependency anomaly detection
 *  - likely root-cause ranking
 *  - config/release correlation
 *  - capacity/cost forecasting
 *  - alert deduplication and routing
 *  - operator impact summary
 *
 * Per spec: "No autonomous production change at initial maturity. Later,
 * only pre-approved low-risk runbooks may be automated with audit/rollback."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAiCopilotService {

    private final LlmProviderRouter llmRouter;

    // -------------------------------------------------------------------------
    // Incident summarization
    // -------------------------------------------------------------------------

    /**
     * Summarize an incident from raw log/metric/trace fragments.
     * Returns a structured summary suitable for an on-call handoff.
     */
    public IncidentSummary summarizeIncident(IncidentInput input) {
        if (input == null || (input.logs().isEmpty() && input.metrics().isEmpty())) {
            return new IncidentSummary(
                    "UNKNOWN",
                    "Insufficient data",
                    "Insufficient data to produce a summary. Provide logs and metrics.",
                    List.of(),
                    List.of(),
                    "P5"
            );
        }

        String severity = inferSeverity(input);
        String headline = buildHeadline(input);
        String narrative = buildNarrative(input, severity);
        List<String> rootCauses = rankRootCauses(input);
        List<String> suggestedActions = suggestActions(severity, rootCauses);

        return new IncidentSummary(severity, headline, narrative, rootCauses, suggestedActions, severity);
    }

    private String inferSeverity(IncidentInput input) {
        // Severity heuristics:
        //  - any error spike AND 5xx count > 100 in 5 min → P1
        //  - any error spike AND elevated 5xx → P2
        //  - latency p95 > 2s for 10+ min → P2
        //  - warnings only → P3
        //  - otherwise → P4
        boolean hasErrorSpike = input.metrics().stream()
                .anyMatch(m -> "5xx_rate".equals(m.name()) && m.value() > 5.0);
        boolean hasHighLatency = input.metrics().stream()
                .anyMatch(m -> "p95_latency_ms".equals(m.name()) && m.value() > 2000);
        double errorCount = input.metrics().stream()
                .filter(m -> "5xx_count".equals(m.name()))
                .mapToDouble(Metric::value).sum();

        if (hasErrorSpike && errorCount > 100) return "P1";
        if (hasErrorSpike || hasHighLatency) return "P2";
        if (input.logs().stream().anyMatch(l -> l.severity().equalsIgnoreCase("WARN"))) return "P3";
        return "P4";
    }

    private String buildHeadline(IncidentInput input) {
        // Find the most frequent error in logs
        return input.logs().stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.severity()))
                .collect(Collectors.groupingBy(LogEntry::message, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Service degradation detected");
    }

    private String buildNarrative(IncidentInput input, String severity) {
        long errorCount = input.logs().stream()
                .filter(l -> "ERROR".equalsIgnoreCase(l.severity()))
                .count();
        long warnCount = input.logs().stream()
                .filter(l -> "WARN".equalsIgnoreCase(l.severity()))
                .count();
        String span = input.startTime() != null && input.endTime() != null
                ? formatDuration(Duration.between(input.startTime(), input.endTime()))
                : "unknown";
        return String.format(
                "%s incident spanning %s. %d error logs, %d warnings. Services: %s. Affected metrics: %s.",
                severity,
                span,
                errorCount,
                warnCount,
                input.affectedServices().isEmpty() ? "unknown" : String.join(", ", input.affectedServices()),
                input.metrics().stream().map(Metric::name).distinct().collect(Collectors.joining(", "))
        );
    }

    private List<String> rankRootCauses(IncidentInput input) {
        // Heuristic root-cause ranking based on log patterns
        Map<String, Integer> patterns = new HashMap<>();
        for (LogEntry log : input.logs()) {
            String msg = log.message().toLowerCase();
            if (msg.contains("connection refused") || msg.contains("connection timeout")) {
                patterns.merge("Downstream service unreachable", 1, Integer::sum);
            }
            if (msg.contains("circuit breaker") || msg.contains("open")) {
                patterns.merge("Circuit breaker open — upstream failure", 1, Integer::sum);
            }
            if (msg.contains("oom") || msg.contains("out of memory") || msg.contains("heap")) {
                patterns.merge("Memory pressure (OOM)", 1, Integer::sum);
            }
            if (msg.contains("rate limit") || msg.contains("429")) {
                patterns.merge("Rate limiting triggered", 1, Integer::sum);
            }
            if (msg.contains("database") || msg.contains("sql") || msg.contains("jdbc")) {
                patterns.merge("Database slowness or failure", 1, Integer::sum);
            }
            if (msg.contains("auth") || msg.contains("token") || msg.contains("unauthorized")) {
                patterns.merge("Auth/token issue", 1, Integer::sum);
            }
        }

        return patterns.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> suggestActions(String severity, List<String> rootCauses) {
        List<String> actions = new ArrayList<>();
        if ("P1".equals(severity)) {
            actions.add("Page on-call engineer immediately");
            actions.add("Open incident bridge and start customer comms template");
        }
        for (String cause : rootCauses) {
            if (cause.contains("Downstream")) actions.add("Verify downstream service health in dashboards");
            if (cause.contains("Circuit breaker")) actions.add("Check upstream provider status page");
            if (cause.contains("Memory")) actions.add("Check JVM heap, capture heap dump if OOM");
            if (cause.contains("Rate limit")) actions.add("Identify offending tenant / IP and apply throttle");
            if (cause.contains("Database")) actions.add("Check slow query log and DB connection pool");
            if (cause.contains("Auth")) actions.add("Verify JWKS endpoint and recent key rotation");
        }
        if (actions.isEmpty()) {
            actions.add("Capture more logs and metrics, escalate to on-call if it persists");
        }
        return actions.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private static String formatDuration(Duration d) {
        long sec = d.getSeconds();
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec / 60) + "m";
        if (sec < 86400) return (sec / 3600) + "h";
        return (sec / 86400) + "d";
    }

    // -------------------------------------------------------------------------
    // Alert deduplication
    // -------------------------------------------------------------------------

    public List<DeduplicatedAlert> deduplicateAlerts(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) return List.of();
        Map<String, List<Alert>> byKey = new HashMap<>();
        for (Alert a : alerts) {
            String key = a.service() + "|" + a.alertName();
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        List<DeduplicatedAlert> out = new ArrayList<>();
        for (Map.Entry<String, List<Alert>> e : byKey.entrySet()) {
            List<Alert> group = e.getValue();
            Alert first = group.get(0);
            Instant earliest = group.stream().map(Alert::timestamp).min(Comparator.naturalOrder()).orElse(first.timestamp());
            Instant latest = group.stream().map(Alert::timestamp).max(Comparator.naturalOrder()).orElse(first.timestamp());
            out.add(new DeduplicatedAlert(
                    e.getKey(),
                    first.service(),
                    first.alertName(),
                    first.severity(),
                    earliest,
                    latest,
                    group.size(),
                    "DEDUPLICATED"));
        }
        // Sort by severity then by count desc
        out.sort((a, b) -> {
            int s = severityRank(a.severity()) - severityRank(b.severity());
            return s != 0 ? s : Integer.compare(b.count(), a.count());
        });
        return out;
    }

    private int severityRank(String s) {
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }

    // -------------------------------------------------------------------------
    // Capacity forecast
    // -------------------------------------------------------------------------

    public CapacityForecast forecastCapacity(List<Double> historical, int horizonDays) {
        if (historical == null || historical.size() < 3) {
            return new CapacityForecast(0.0, 0.0, List.of(), horizonDays, "INSUFFICIENT_DATA");
        }
        // Simple linear regression
        int n = historical.size();
        double meanX = (n - 1) / 2.0;
        double meanY = historical.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - meanX) * (historical.get(i) - meanY);
            den += (i - meanX) * (i - meanX);
        }
        double slope = den == 0 ? 0 : num / den;
        double intercept = meanY - slope * meanX;

        List<Double> forecast = new ArrayList<>();
        for (int d = 1; d <= horizonDays; d++) {
            double predicted = intercept + slope * (n - 1 + d);
            forecast.add(Math.max(0, predicted));
        }
        return new CapacityForecast(slope, intercept, forecast, horizonDays, "LINEAR");
    }

    // -------------------------------------------------------------------------
    // Types
    // -------------------------------------------------------------------------

    public record IncidentInput(
            List<LogEntry> logs,
            List<Metric> metrics,
            List<String> affectedServices,
            Instant startTime,
            Instant endTime
    ) {}

    public record LogEntry(String severity, String message, Instant timestamp) {}

    public record Metric(String name, double value, Instant timestamp) {}

    public record IncidentSummary(
            String severity,
            String headline,
            String narrative,
            List<String> rootCauses,
            List<String> suggestedActions,
            String priority
    ) {}

    public record Alert(
            String service,
            String alertName,
            String severity,
            Instant timestamp
    ) {}

    public record DeduplicatedAlert(
            String key,
            String service,
            String alertName,
            String severity,
            Instant firstSeen,
            Instant lastSeen,
            int count,
            String status
    ) {}

    public record CapacityForecast(
            double slope,
            double intercept,
            List<Double> forecast,
            int horizonDays,
            String model
    ) {}

    // -------------------------------------------------------------------------
    // Config / release correlation
    // -------------------------------------------------------------------------

    public record ConfigChangeEvent(
            String tenantId,
            String changeType,    // LAYOUT_PUBLISH, JOURNEY_PUBLISH, THEME_PUBLISH, FEATURE_FLAG_CHANGE, KILL_SWITCH
            String resourceId,    // e.g., the layout or feature flag id
            String version,       // configVersion or feature-flag version
            String actor,         // admin user id
            Instant occurredAt
    ) {
        public String getTenantId() {
            return tenantId;
        }

        public String getChangeType() {
            return changeType;
        }

        public String getResourceId() {
            return resourceId;
        }

        public String getVersion() {
            return version;
        }

        public String getActor() {
            return actor;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    public record ReleaseEvent(
            String tenantId,
            String serviceName,
            String version,
            String commitSha,
            String actor,
            Instant deployedAt
    ) {
        public String getTenantId() {
            return tenantId;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getVersion() {
            return version;
        }

        public String getCommitSha() {
            return commitSha;
        }

        public String getActor() {
            return actor;
        }

        public Instant getDeployedAt() {
            return deployedAt;
        }
    }

    public record CorrelationResult(
            String incidentId,
            String correlatedType,    // CONFIG_CHANGE | RELEASE | NONE
            String correlatedId,
            Instant occurredAt,
            long minutesBeforeIncident,
            double confidence,
            String explanation
    ) {}

    /**
     * Correlate an incident with recent config changes and deployments.
     *
     * Given an incident window (start, end) and a list of config/release events,
     * returns the single most likely correlation or NONE. Confidence is the
     * share of the time gap spent before the incident started.
     *
     * Use case: when an SRE says "since 09:42 the dashboard is broken", this
     * method can say "A layout publish for tenant dialog-lk happened at 09:39,
     * 3 minutes before the incident, confidence 0.95" — or "no significant
     * config/release event in the window".
     */
    public CorrelationResult correlateConfigAndReleases(
            String incidentId,
            Instant incidentStart,
            IncidentInput input,
            List<ConfigChangeEvent> configEvents,
            List<ReleaseEvent> releaseEvents) {

        // Look at the 2-hour window before the incident — most regressions
        // appear within this timeframe.
        Instant lookbackStart = incidentStart.minus(Duration.ofHours(2));

        // 1. Correlate config changes
        CorrelationResult bestConfig = null;
        for (ConfigChangeEvent c : configEvents) {
            if (c.getOccurredAt() == null) continue;
            if (c.getOccurredAt().isBefore(lookbackStart)) continue;
            if (c.getOccurredAt().isAfter(incidentStart)) continue;
            long minutesBefore = Duration.between(c.getOccurredAt(), incidentStart).toMinutes();
            // Confidence decays with time gap: 0 min = 1.0, 120 min = 0.1
            double confidence = Math.max(0.1, 1.0 - (minutesBefore / 120.0));
            // Higher weight for HIGH-risk change types
            if ("KILL_SWITCH".equals(c.getChangeType())) confidence = Math.min(1.0, confidence + 0.1);
            if ("FEATURE_FLAG_CHANGE".equals(c.getChangeType())) confidence = Math.min(1.0, confidence + 0.05);
            // Tenant match
            String affectedTenant = input.affectedServices() != null && !input.affectedServices().isEmpty()
                    ? input.affectedServices().get(0) : null;
            if (affectedTenant != null && !affectedTenant.equals(c.getTenantId())) {
                confidence *= 0.5; // Wrong tenant — halve confidence
            }
            String explanation = String.format(
                    "%s %s for tenant=%s resource=%s at %s (tenant-match=%s)",
                    c.getChangeType(),
                    c.getVersion() != null ? "v" + c.getVersion() : "",
                    c.getTenantId(),
                    c.getResourceId(),
                    c.getOccurredAt(),
                    affectedTenant == null || affectedTenant.equals(c.getTenantId()));
            CorrelationResult r = new CorrelationResult(
                    incidentId, "CONFIG_CHANGE",
                    c.getResourceId(), c.getOccurredAt(),
                    minutesBefore, confidence, explanation);
            if (bestConfig == null || r.confidence() > bestConfig.confidence()) {
                bestConfig = r;
            }
        }

        // 2. Correlate releases
        CorrelationResult bestRelease = null;
        for (ReleaseEvent e : releaseEvents) {
            if (e.getDeployedAt() == null) continue;
            if (e.getDeployedAt().isBefore(lookbackStart)) continue;
            if (e.getDeployedAt().isAfter(incidentStart)) continue;
            long minutesBefore = Duration.between(e.getDeployedAt(), incidentStart).toMinutes();
            double confidence = Math.max(0.1, 1.0 - (minutesBefore / 120.0));
            String explanation = String.format(
                    "Release of %s version=%s at %s (sha=%s)",
                    e.getServiceName(), e.getVersion(), e.getDeployedAt(), e.getCommitSha());
            CorrelationResult r = new CorrelationResult(
                    incidentId, "RELEASE",
                    e.getServiceName() + ":" + e.getVersion(),
                    e.getDeployedAt(), minutesBefore, confidence, explanation);
            if (bestRelease == null || r.confidence() > bestRelease.confidence()) {
                bestRelease = r;
            }
        }

        // Pick the higher-confidence correlation
        if (bestConfig != null && bestRelease != null) {
            return bestConfig.confidence() >= bestRelease.confidence() ? bestConfig : bestRelease;
        }
        if (bestConfig != null) return bestConfig;
        if (bestRelease != null) return bestRelease;
        return new CorrelationResult(incidentId, "NONE", null, null, 0, 0.0,
                "No significant config change or release in the 2h window before the incident.");
    }
}
