package com.omobio.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Dashboard BFF — Resilient Widget Orchestrator
 *
 * This is the most critical service for customer experience. It orchestrates
 * all dashboard widgets concurrently with bounded deadline and partial response.
 *
 * ADR-008: Partial dashboard response
 *   - 25 configured widgets launch concurrently within bounded concurrency
 *   - Overall deadline returns partial results
 *   - Failed widget can be refreshed alone
 *   - Write operations are not auto-retried without idempotency
 *
 * Widget execution model:
 *
 *   GET /api/v1/dashboard/home
 *        |
 *        v
 *   load compiled profile from process memory
 *        |
 *        v
 *   launch widget providers concurrently
 *        |
 *        +--> balance provider ---- timeout/bulkhead/circuit breaker
 *        +--> usage provider ------ timeout/bulkhead/circuit breaker
 *        +--> bill provider ------- timeout/bulkhead/circuit breaker
 *        +--> offers read model --- cache/read store
 *        +--> banners ------------ CDN/config
 *        +--> AI recommendation --- precomputed/read model
 *        |
 *   overall deadline
 *        |
 *   return SUCCESS + PARTIAL + STALE + TIMEOUT statuses
 *
 * Per-widget status codes:
 *   SUCCESS    — data returned
 *   PARTIAL    — some data returned (e.g., some items filtered)
 *   STALE      — cached data returned (upstream delayed)
 *   TIMEOUT    — widget timed out
 *   UNAVAILABLE — widget not available for this user
 *   ERROR      — widget failed
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.omobio.dashboard",
    "com.omobio.platform.common"
})
public class DashboardBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardBffApplication.class, args);
    }
}