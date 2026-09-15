package com.selfcare.platform.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DB-backed {@link TenantValidator} — the canonical implementation.
 *
 * Reads tenant registry state from the {@code tenant_configs} Mongo collection
 * (the platform's config source of truth, written by config-tenant-service /
 * selfcare Studio). Registered as {@code @Primary} so the platform-common
 * {@link TenantResolverFilter} uses live DB state instead of an in-memory
 * {@code selfcare.tenants} map.
 *
 * Why this replaces the config-file validator:
 * - config-file tenants were removed everywhere (nothing hardcoded rule), so the
 *   old {@link TenantProperties} (empty map) rejected every request.
 * - a remote HTTP call back to config-tenant-service for every request fails
 *   on config-tenant-service itself (its own TenantResolverFilter would need to
 *   resolve tenants) and needs a JWT — a circular / masked-401 trap.
 * - direct Mongo reads are cheap (indexed on tenantId) and are cached in-process
 *   with a short TTL so the per-request filter never blocks on the DB.
 *
 * Fail-closed: if the lookup fails or the tenant is unknown/suspended, requests
 * are rejected with a clear 400 (never a masked 401).
 */
@Slf4j
@Component
@Primary
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MongoTenantValidator implements TenantValidator {

    private static final String COLLECTION = "tenant_configs";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PROVIDER_BINDINGS = "providerBindings";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String DEFAULT_ADAPTER_KEY = "default";

    private final MongoTemplate mongoTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public MongoTenantValidator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        log.info("Mongo-backed TenantValidator active (collection: {})", COLLECTION);
    }

    @Override
    public boolean isTenantActive(String tenantId) {
        String key = normalize(tenantId);
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.isFresh()) {
            return entry.isActive;
        }
        Document doc = fetch(key);
        boolean active = doc != null && ACTIVE_STATUS.equalsIgnoreCase(string(doc, FIELD_STATUS));
        cache.put(key, new CacheEntry(active, active ? adapterFrom(doc) : null));
        return active;
    }

    @Override
    public String getAdapterPackage(String tenantId) {
        String key = normalize(tenantId);
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.isFresh()) {
            return entry.adapterPackage;
        }
        if (entry != null && !entry.isActive) {
            return null;
        }
        Document doc = fetch(key);
        boolean active = doc != null && ACTIVE_STATUS.equalsIgnoreCase(string(doc, FIELD_STATUS));
        String adapter = active ? adapterFrom(doc) : null;
        cache.put(key, new CacheEntry(active, adapter));
        return adapter;
    }

    private Document fetch(String tenantId) {
        try {
            Query query = new Query(Criteria.where(FIELD_TENANT_ID).is(tenantId));
            return mongoTemplate.findOne(query, Document.class, COLLECTION);
        } catch (Exception e) {
            log.warn("Tenant lookup failed for '{}': {}", tenantId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String adapterFrom(Document doc) {
        if (doc == null) {
            return null;
        }
        Object bindings = doc.get(FIELD_PROVIDER_BINDINGS);
        if (bindings instanceof Map<?, ?> map) {
            Object adapter = ((Map<String, Object>) map).get(DEFAULT_ADAPTER_KEY);
            return adapter == null ? null : adapter.toString();
        }
        return null;
    }

    private static String string(Document doc, String field) {
        Object value = doc.get(field);
        return value == null ? null : value.toString();
    }

    private static String normalize(String tenantId) {
        return tenantId == null ? "" : tenantId.trim().toLowerCase();
    }

    private static final class CacheEntry {
        final boolean isActive;
        final String adapterPackage;
        final long expiresAtNanos;

        CacheEntry(boolean isActive, String adapterPackage) {
            this.isActive = isActive;
            this.adapterPackage = adapterPackage;
            this.expiresAtNanos = System.nanoTime() + CACHE_TTL.toNanos();
        }

        boolean isFresh() {
            return System.nanoTime() < expiresAtNanos;
        }
    }
}