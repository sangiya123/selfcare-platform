package com.selfcare.dashboard.service;

import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Dashboard layout service — resolves which widgets to show per tenant/profile.
 *
 * Resolution order:
 * 1. Check Redis cache for tenant-specific layout
 * 2. Fall back to MongoDB layout_documents collection
 * 3. Fall back to the default widget set for the industry
 *
 * Layout documents define:
 * - Which widgets to show for a given profile key
 * - Widget ordering
 * - Widget-specific config overrides
 *
 * Default widget sets by industry:
 * - TELCO: balance, usage, bill, quick-actions, notifications, bundles, banners
 * - INSURANCE: balance, quick-actions, notifications, insurance-policies, banners
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardLayoutService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${dashboard.layout.default-ttl-seconds:300}")
    private long layoutCacheTtl;

    private static final String LAYOUT_CACHE_PREFIX = "selfcare:dashboard:layout:";
    private static final String DEFAULT_INDUSTRY = "TELCO";

    // Default widget order per industry
    private static final Map<String, List<String>> DEFAULT_LAYOUTS = Map.of(
            "TELCO", List.of("balance", "usage", "bill", "quick-actions", "notifications", "bundles", "banners"),
            "INSURANCE", List.of("balance", "quick-actions", "notifications", "insurance-policies", "banners"),
            "TRAVEL", List.of("balance", "quick-actions", "notifications", "banners")
    );

    /**
     * Resolve the widget list for a tenant + profile.
     *
     * @param tenantId   the tenant identifier
     * @param profileKey the experience profile key (e.g., "PREPAID", "POSTPAID", "PREMIUM")
     * @return ordered list of widget IDs to execute
     */
    public List<String> resolveLayout(String tenantId, String profileKey) {
        // 1. Try cache
        String cacheKey = LAYOUT_CACHE_PREFIX + tenantId + ":" + (profileKey != null ? profileKey : "default");
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> cachedList = (List<String>) cached;
            log.debug("Layout cache hit: tenant={}, profile={}", tenantId, profileKey);
            return cachedList;
        }

        // 2. Try MongoDB (layout_documents collection)
        List<String> layout = loadLayoutFromMongo(tenantId, profileKey);

        // 3. Fall back to default
        if (layout == null || layout.isEmpty()) {
            layout = getDefaultLayout(tenantId);
            log.info("Using default layout for tenant={}, profile={}: {}", tenantId, profileKey, layout);
        }

        // Cache for next time
        if (layout != null && !layout.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, layout, Duration.ofSeconds(layoutCacheTtl));
            } catch (Exception e) {
                log.debug("Failed to cache layout: {}", e.getMessage());
            }
        }

        return layout;
    }

    /**
     * Load layout from MongoDB dashboard_layouts for a tenant/profile.
     * Profile-specific layout wins; a tenant-level layout is the fallback.
     * Returns null when nothing is authored, triggering the default layout.
     */
    @SuppressWarnings("unchecked")
    private List<String> loadLayoutFromMongo(String tenantId, String profileKey) {
        try {
            if (profileKey != null && !profileKey.isBlank()) {
                Query profileQuery = new Query();
                profileQuery.addCriteria(Criteria.where("tenantId").is(tenantId)
                        .and("active").is(true)
                        .and("profileKey").is(profileKey));
                DashboardLayoutDocument doc = mongoTemplate.findOne(profileQuery, DashboardLayoutDocument.class);
                if (doc != null && doc.getWidgetOrder() != null && !doc.getWidgetOrder().isEmpty()) {
                    log.info("Dashboard layout resolved from Mongo (tenant={}, profile={})", tenantId, profileKey);
                    return doc.getWidgetOrder();
                }
            }

            Query tenantQuery = new Query();
            tenantQuery.addCriteria(Criteria.where("tenantId").is(tenantId)
                    .and("active").is(true));
            DashboardLayoutDocument doc = mongoTemplate.findOne(tenantQuery, DashboardLayoutDocument.class);
            if (doc != null && doc.getWidgetOrder() != null && !doc.getWidgetOrder().isEmpty()) {
                log.info("Dashboard layout resolved from Mongo (tenant={}, default profile)", tenantId);
                return doc.getWidgetOrder();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve dashboard layout from Mongo for tenant={}: {}", tenantId, e.getMessage());
        }
        return null;
    }

    /**
     * Get the default layout for a tenant based on its industry.
     * Falls back to TELCO layout if industry is unknown.
     */
    private List<String> getDefaultLayout(String tenantId) {
        String industry = resolveIndustry(tenantId);
        return DEFAULT_LAYOUTS.getOrDefault(industry, DEFAULT_LAYOUTS.get(DEFAULT_INDUSTRY));
    }

    /**
     * Resolve the industry for a tenant.
     * In production: read from tenant configuration service.
     */
    private String resolveIndustry(String tenantId) {
        // Stub: return from config or default to TELCO
        return DEFAULT_INDUSTRY;
    }

    /**
     * Invalidate the layout cache for a tenant.
     * Called when a tenant's layout is updated.
     */
    public void invalidateCache(String tenantId) {
        String pattern = LAYOUT_CACHE_PREFIX + tenantId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Invalidated layout cache for tenant={}: {} keys", tenantId, keys.size());
        }
    }
}
