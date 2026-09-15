package com.selfcare.platform.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Platform Common — shared foundation library.
 *
 * This module is NOT a runnable application. It provides:
 * - TenantContext: request-scoped tenant identity
 * - TenantResolverFilter: extracts X-Tenant-Id from request headers
 * - ApiAdapterRegistry: operator-pluggable provider pattern
 * - FeatureFlagClient: tenant-aware feature flag checks
 * - Standard error envelope and correlation ID propagation
 * - JWT security configuration helpers
 * - OpenTelemetry setup
 *
 * Every platform service depends on this module.
 * See CLAUDE.md for patterns and usage examples.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class PlatformCommonApplication {

    public static void main(String[] args) {
        // This is a library module — not a runnable application.
        // The main method exists only to enable Spring Boot's annotation
        // processing and auto-configuration scanning.
        SpringApplication.run(PlatformCommonApplication.class, args);
    }
}
