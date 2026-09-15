package com.selfcare.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * API Gateway — Single entry point for all Selfcare platform traffic.
 *
 * Responsibilities:
 * - Tenant resolution (X-Tenant-Id header propagation)
 * - Authentication (JWT validation via JWKS)
 * - Authorization (RBAC scopes)
 * - Rate limiting (per-tenant, per-user, per-endpoint)
 * - Circuit breaking (resilience4j per downstream)
 * - Strangler-fig routing to legacy PHP for unmigrated paths
 * - Request/response transformation
 * - Correlation ID propagation
 * - Standard error envelope
 * - WebSocket upgrade for real-time features
 * - CORS handling
 *
 * Architecture (Doc 1 sec 4.3):
 *   Mobile/Web -> API Gateway -> Experience Resolver / BFF / Services
 *                |
 *                +-> Legacy PHP (strangler-fig fallback for unmigrated paths)
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
    "com.selfcare.gateway",
    "com.selfcare.platform.common"
})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}