package com.omobio.dashboard.service;

import com.omobio.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * WebClient-based HTTP client for calling downstream services.
 *
 * Uses Spring Cloud LoadBalancer for service discovery.
 * All downstream calls inherit tenant context headers.
 *
 * Service names (matches docker-compose service names):
 *   usage-service      — balance and usage
 *   billing-service    — bills and invoices
 *   product-service    — plans, bundles
 *   notification-service — notifications
 *   content-service    — banners, articles
 *   insurance-service  — insurance policies (insurance tenants)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardClient {

    private final WebClient.Builder webClientBuilder;
    private final ReactorLoadBalancerExchangeFilterFunction loadBalancerFilter;

    @Value("${dashboard.client.timeout-ms:500}")
    private int timeoutMs;

    @Value("${dashboard.client.retry-attempts:0}")
    private int retryAttempts;

    private WebClient buildClient() {
        return webClientBuilder
                .filter(loadBalancerFilter)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Tenant-Id", TenantContext.get().getTenantId())
                .defaultHeader("X-Correlation-Id", TenantContext.get().getCorrelationId())
                .build();
    }

    // -------------------------------------------------------------------------
    // Usage Service
    // -------------------------------------------------------------------------

    /**
     * Get balance from usage-service.
     */
    public Mono<Object> getBalance(String connectionId) {
        return buildClient()
                .get()
                .uri("lb://usage-service/api/v1/balance/{connectionId}", connectionId)
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("usage-service balance failed: connectionId={}, error={}", connectionId, e.getMessage()));
    }

    /**
     * Get usage summary from usage-service.
     */
    public Mono<Object> getUsage(String connectionId) {
        return buildClient()
                .get()
                .uri("lb://usage-service/api/v1/usage/{connectionId}", connectionId)
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("usage-service usage failed: connectionId={}, error={}", connectionId, e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Billing Service
    // -------------------------------------------------------------------------

    /**
     * Get current bill from billing-service.
     */
    public Mono<Object> getCurrentBill(String connectionId) {
        return buildClient()
                .get()
                .uri("lb://billing-service/api/v1/bills/current/{connectionId}", connectionId)
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("billing-service current bill failed: connectionId={}, error={}", connectionId, e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Product Service
    // -------------------------------------------------------------------------

    /**
     * Get featured bundles from product-service.
     */
    public Mono<Object> getFeaturedBundles(String tenantId, String lob) {
        return buildClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("product-service")
                        .path("/api/v1/bundles/featured")
                        .queryParam("tenantId", tenantId)
                        .queryParam("lob", lob)
                        .queryParam("limit", 5)
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("product-service featured bundles failed: tenantId={}, error={}", tenantId, e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Notification Service
    // -------------------------------------------------------------------------

    /**
     * Get recent notifications from notification-service.
     */
    public Mono<Object> getRecentNotifications(String userId, int limit) {
        return buildClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("notification-service")
                        .path("/api/v1/notifications")
                        .queryParam("userId", userId)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("notification-service notifications failed: userId={}, error={}", userId, e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Content Service
    // -------------------------------------------------------------------------

    /**
     * Get active banners from content-service.
     */
    public Mono<Object> getActiveBanners(String tenantId, String channel) {
        return buildClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("content-service")
                        .path("/api/v1/banners")
                        .queryParam("tenantId", tenantId)
                        .queryParam("channel", channel != null ? channel : "APP")
                        .queryParam("status", "ACTIVE")
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("content-service banners failed: tenantId={}, error={}", tenantId, e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Insurance Service
    // -------------------------------------------------------------------------

    /**
     * Get insurance policies from insurance-service.
     */
    public Mono<Object> getInsurancePolicies(String userId) {
        return buildClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host("insurance-service")
                        .path("/api/v1/policies")
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .doOnError(e -> log.warn("insurance-service policies failed: userId={}, error={}", userId, e.getMessage()));
    }
}
