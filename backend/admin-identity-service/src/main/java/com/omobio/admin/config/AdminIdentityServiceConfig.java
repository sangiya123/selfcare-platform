package com.omobio.admin.config;

import com.omobio.admin.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Startup initializer — seeds system roles for built-in tenants.
 */
@Component
public class AdminIdentityServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminIdentityServiceConfig.class);

    private final RoleService roleService;
    private final RedisTemplate<String, Object> redis;

    /** Built-in tenants that need system roles seeded. */
    private static final String[] PLATFORM_TENANTS = {
        "omobio-platform",  // OMOBIO internal platform admin
        "dialog-lk",        // Dialog telco
        "hutch-lk",         // Hutch telco
        "airtel-lk",        // Airtel telco
        "aia-lk"            // AIA insurance
    };

    public AdminIdentityServiceConfig(RoleService roleService,
                                       RedisTemplate<String, Object> redis) {
        this.roleService = roleService;
        this.redis = redis;
    }

    private final AtomicBoolean seeded = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!seeded.compareAndSet(false, true)) return;
        for (String tenant : PLATFORM_TENANTS) {
            try {
                roleService.seedSystemRolesForTenant(tenant);
                log.info("Seeded system roles for tenant: {}", tenant);
            } catch (Exception e) {
                log.warn("Failed to seed roles for {} (may be OK in dev): {}", tenant, e.getMessage());
            }
        }
    }
}
