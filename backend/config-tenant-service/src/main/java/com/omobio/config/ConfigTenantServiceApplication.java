package com.omobio.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Config Tenant Service
 *
 * Serves all operator configuration: themes, layouts, navigation, journeys, providers, content.
 *
 * Architecture:
 *   Mobile/Web -> Config SDK -> Config Tenant Service
 *                                       |
 *                                       +-> MongoDB (source of truth, versioned)
 *                                       +-> Redis (hot cache)
 *                                       +-> Config Compiler (compile at publish time)
 *
 * Key responsibilities:
 * 1. Tenant management (CRUD)
 * 2. Theme config (design tokens, colors, fonts, branding)
 * 3. Layout config (page structure, component placement)
 * 4. Navigation config (tabs, menus, routes, deep links)
 * 5. Experience config (profile -> section resolution)
 * 6. Feature flags (via Unleash integration)
 * 7. Provider bindings (operator-specific adapter registration)
 * 8. Config publish lifecycle (draft -> validate -> preview -> approve -> publish)
 * 9. Compiled manifest serving (immutable, versioned)
 *
 * Performance targets:
 * - Config served from in-process memory cache
 * - No Mongo read on every request
 * - App stores last-known-good manifest
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.omobio.config",
    "com.omobio.platform.common"
})
public class ConfigTenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigTenantServiceApplication.class, args);
    }
}