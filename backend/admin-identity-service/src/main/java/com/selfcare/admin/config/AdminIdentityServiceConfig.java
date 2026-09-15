package com.selfcare.admin.config;

import com.selfcare.admin.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Startup initializer — seeds system roles for every tenant registered in the
 * Mongo {@code tenant_configs} collection (the platform's DB-backed tenant
 * registry). Nothing is hardcoded: adding a tenant in the config DB automatically
 * provisions its system roles on next admin-identity startup.
 */
@Component
public class AdminIdentityServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminIdentityServiceConfig.class);

    private static final String TENANT_COLLECTION = "tenant_configs";
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_STATUS = "status";
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final RoleService roleService;
    private final MongoTemplate mongoTemplate;

    private final AtomicBoolean seeded = new AtomicBoolean(false);

    public AdminIdentityServiceConfig(RoleService roleService, MongoTemplate mongoTemplate) {
        this.roleService = roleService;
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!seeded.compareAndSet(false, true)) return;

        Set<String> tenants;
        try {
            tenants = loadActiveTenantIds();
        } catch (Exception e) {
            log.warn("Could not load tenants from tenant_configs (roles seed skipped): {}", e.getMessage());
            return;
        }

        for (String tenant : tenants) {
            try {
                roleService.seedSystemRolesForTenant(tenant);
                log.info("Seeded system roles for tenant: {}", tenant);
            } catch (Exception e) {
                log.warn("Failed to seed roles for {} (may be OK in dev): {}", tenant, e.getMessage());
            }
        }
    }

    /**
     * Reads the distinct {@code tenantId}s of ACTIVE tenants from the Mongo
     * {@code tenant_configs} collection — the DB source of truth. No tenant is
     * hardcoded in code or configuration.
     */
    private Set<String> loadActiveTenantIds() {
        Query query = new Query(Criteria.where(FIELD_STATUS).is(ACTIVE_STATUS));
        List<String> ids = mongoTemplate.findDistinct(
                query, FIELD_TENANT_ID, TENANT_COLLECTION, String.class);
        return ids.stream().filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
    }
}