package com.selfcare.config.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a navigation reference (e.g. "dialog-main@9") to the FLATTENED
 * NavItem[] list consumed by the mobile/web apps.
 *
 * The app contract is a flat array of nav items (see mobile NavItem). The
 * Mongo document groups items into {@code tabs}, {@code drawerItems},
 * {@code quickActions} and {@code routes}; this repository unwraps and merges
 * them, mapping each group to the app's {@code placement} field, so the
 * compiler embeds a manifest shape the app renders directly (ADR-004).
 */
@Slf4j
@Component
public class NavigationRepositoryImpl implements ConfigCompiler.NavigationRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public NavigationRepositoryImpl(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    private static final Map<String, String> TAB_PLACEMENT = Map.of(
            "route", "tabBar",
            "targetType", "IN_APP_SCREEN",
            "requiresAuth", "false",
            "enabled", "true"
    );

    @Override
    public Optional<List<Map<String, Object>>> findByRef(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        String[] parts = ref.split("@");
        String name = parts[0];
        String version = parts.length > 1 ? parts[1] : "1.0.0";

        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        if (tenantId == null || "UNKNOWN".equals(tenantId)) {
            return Optional.empty();
        }

        try {
            Query query = new Query(Criteria.where("tenantId").is(tenantId)
                    .and("name").is(name)
                    .and("version").is(coerceVersion(version))
                    .and("status").is("PUBLISHED"));
            var doc = mongoTemplate.findOne(query, Map.class, "navigation_documents");
            if (doc == null) {
                log.warn("Navigation document not found for ref={} (tenantId={}, name={}, version={})",
                        ref, tenantId, name, coerceVersion(version));
                return Optional.of(List.of());
            }
            List<Map<String, Object>> items = flatten(doc);
            log.info("Navigation resolved for ref={}: {} items (tabs={}, drawer={}, quick={})",
                    ref, items.size(),
                    groupSize(doc, "tabs"), groupSize(doc, "drawerItems"), groupSize(doc, "quickActions"));
            return Optional.of(items);
        } catch (Exception e) {
            log.warn("Navigation lookup failed for ref={}: {}", ref, e.getMessage());
            return Optional.of(List.of());
        }
    }

    /**
     * Mongo stores versions as numbers ("1") while compile refs carry strings —
     * coerce numeric-looking refs to a Number so the equality match works.
     */
    private static Object coerceVersion(String version) {
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException ignored) {
            return version;
        }
    }

    /**
     * Unwrap the navigation document groups into flat NavItem entries.
     * Group mapping:
     *   tabs          -> placement: "tabBar"
     *   drawerItems   -> placement: "hamburger"
     *   quickActions  -> placement: "quickActions"
     *   routes        -> placement: "subPage" (resolvable deep links)
     * Each entry keeps id/label/icon/route plus per-app flags (requiresAuth,
     * enabled) when present in the doc.
     */
    List<Map<String, Object>> flatten(Map<String, Object> doc) {
        List<Map<String, Object>> items = new ArrayList<>();
        flattenGroup(doc.get("tabs"), "tabBar", items);
        flattenGroup(doc.get("drawerItems"), "hamburger", items);
        flattenGroup(doc.get("quickActions"), "quickActions", items);
        flattenGroup(doc.get("routes"), "subPage", items);
        return items;
    }

    private void flattenGroup(Object group, String placement, List<Map<String, Object>> out) {
        if (!(group instanceof List<?> list)) return;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> raw)) continue;
            Map<String, Object> src = new LinkedHashMap<>();
            raw.forEach((k, v) -> src.put(String.valueOf(k), v));
            if (src.get("id") == null && src.get("path") != null) {
                src.put("id", src.get("path"));
                src.put("route", src.get("path"));
            }
            if (src.get("route") == null && src.get("path") != null) {
                src.put("route", src.get("path"));
            }
            if (src.get("route") == null) {
                src.put("route", routeFromAction(src.get("action")));
            }
            if (src.get("route") == null) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(src.get("id")));
            if (src.get("label") != null) item.put("label", String.valueOf(src.get("label")));
            if (src.get("labelKey") != null) item.put("labelKey", String.valueOf(src.get("labelKey")));
            if (src.get("icon") != null) item.put("icon", String.valueOf(src.get("icon")));
            if (src.get("iconUrl") != null) item.put("iconUrl", String.valueOf(src.get("iconUrl")));
            item.put("route", String.valueOf(src.get("route")));
            item.put("placement", placement);
            Object req = src.get("requiresAuth") != null ? src.get("requiresAuth") : src.get("requiresAuthentication");
            if (req != null) item.put("requiresAuth", asBoolean(req, false));
            if (src.get("enabled") != null) item.put("enabled", asBoolean(src.get("enabled"), true));
            if (src.get("targetType") != null) item.put("targetType", String.valueOf(src.get("targetType")));
            if (src.get("navParams") != null) item.put("navParams", src.get("navParams"));
            // Preserve the launch action (journeyId / url / params / analyticsEvent)
            // for quick actions and other action-driven items (ADR-009 closed set).
            if (src.get("action") instanceof Map<?, ?> action) {
                Map<String, Object> kept = new LinkedHashMap<>();
                action.forEach((k, v) -> kept.put(String.valueOf(k), v));
                item.put("action", kept);
                if (kept.get("journeyId") != null) item.put("journeyId", String.valueOf(kept.get("journeyId")));
                if (src.get("navParams") == null && kept.get("params") instanceof Map<?, ?> params) {
                    item.put("navParams", params);
                }
            }
            if (src.get("children") != null) {
                List<Map<String, Object>> children = new ArrayList<>();
                flattenGroup(src.get("children"), placement, children);
                if (!children.isEmpty()) item.put("children", children);
            }
            out.add(item);
        }
    }

    /**
     * Quick actions (and other legacy nav entries) express their target as a
     * nested {@code action} map ({@code type}, {@code journeyId}, {@code route},
     * {@code url}) rather than a top-level {@code route}. Derive a route so the
     * app can open them. START_JOURNEY items fall back to their journeyId; both
     * the derived route and the preserved action are emitted (see flattenGroup).
     */
    private static String routeFromAction(Object actionObj) {
        if (!(actionObj instanceof Map<?, ?> action)) return null;
        Object route = action.get("route");
        if (route != null && !String.valueOf(route).isBlank()) return String.valueOf(route);
        Object journeyId = action.get("journeyId");
        if (journeyId != null && !String.valueOf(journeyId).isBlank()) {
            return "/journey/" + journeyId;
        }
        Object url = action.get("url");
        if (url != null && !String.valueOf(url).isBlank()) return String.valueOf(url);
        return null;
    }

    private boolean asBoolean(Object v, boolean fallback) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private int groupSize(Map<String, Object> doc, String key) {
        Object g = doc.get(key);
        return g instanceof List<?> l ? l.size() : 0;
    }
}