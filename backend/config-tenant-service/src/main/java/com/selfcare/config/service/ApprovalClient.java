package com.selfcare.config.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * WebClient-based client for the approval-service REST API.
 *
 * Uses Spring Cloud LoadBalancer for service discovery so that the service
 * name "approval-service" resolves to a pod IP in Kubernetes.
 *
 * All calls inherit the current tenant and correlation IDs from TenantContext.
 *
 * Callers:
 *   LayoutService     — LAYOUT_PUBLISH action
 *   ThemeService      — theme publish (future)
 *   JourneyService    — journey publish (future)
 *
 * The approval flow:
 *   1. Calling service submits request via {@link #submitForApproval}
 *   2. Returns a requestId (UUID) — the calling service stores this
 *      and does NOT apply the change yet
 *   3. Calling service polls {@link #getRequest} or waits for a callback
 *   4. Once APPROVED, the calling service applies the change
 *   5. If REJECTED / CANCELLED / EXPIRED, the change is discarded
 *
 * Failure handling:
 *   - Timeout: treated as transient; the change is NOT applied
 *   - 404: the request does not exist; the change is NOT applied
 *   - 4xx: caller error; the change is NOT applied
 *   - 5xx: server error; the change is NOT applied
 *
 * NOTE: In a production deployment, add circuit-breaker support (Resilience4j
 * or Spring Cloud Circuit Breaker) to avoid cascading failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String APPROVAL_SERVICE = "approval-service:8097";

    @Value("${approval.client.timeout-ms:5000}")
    private int timeoutMs;

    // -------------------------------------------------------------------------
    // Public API — called by LayoutService, ThemeService, etc.
    // -------------------------------------------------------------------------

    /**
     * Submit a change for four-eyes approval.
     *
     * @param tenantId       Tenant context (from TenantContext)
     * @param action         ApprovalAction enum name (e.g. "LAYOUT_PUBLISH")
     * @param resourceType   Logical type (e.g. "layout")
     * @param resourceId     Identifier of the resource (e.g. layout UUID)
     * @param requesterId    Internal admin user ID
     * @param requesterEmail Admin user email
     * @param changeSnapshot Free-form JSON object describing the change
     * @return Mono of the requestId (UUID) on success; empty Mono on failure
     */
    public Mono<String> submitForApproval(
            String tenantId,
            String action,
            String resourceType,
            String resourceId,
            String requesterId,
            String requesterEmail,
            Object changeSnapshot) {

        Map<String, Object> body = Map.of(
                "action", action,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "changeSnapshot", changeSnapshot
        );

        return webClient()
                .post()
                .uri("/api/v1/admin/approvals")
                .header("X-Tenant-Id", tenantId)
                .header("X-User-Id", requesterId)
                .header("X-User-Email", requesterEmail)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(response -> extractRequestId(response))
                .doOnSuccess(id -> log.info("Approval submitted: requestId={}, action={}, resource={}/{}",
                        id, action, resourceType, resourceId))
                .doOnError(e -> log.error("Failed to submit approval: action={}, resource={}/{}, error={}",
                        action, resourceType, resourceId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Get the status of an existing approval request.
     *
     * @param requestId UUID request identifier
     * @return Mono of the status string ("PENDING", "APPROVED", "REJECTED", etc.);
     *         empty Mono on failure
     */
    public Mono<Optional<String>> getRequestStatus(String requestId) {
        return webClient()
                .get()
                .uri("/api/v1/admin/approvals/{requestId}", requestId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(response -> extractStatus(response))
                .doOnError(e -> log.warn("Failed to get approval status: requestId={}, error={}",
                        requestId, e.getMessage()))
                .onErrorResume(e -> Mono.just(Optional.empty()));
    }

    /**
     * Check if an action always requires four-eyes approval.
     *
     * @param action ApprovalAction enum name
     * @return Mono of Boolean; false on failure
     */
    public Mono<Boolean> isApprovalRequired(String action) {
        return webClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/approvals/check")
                        .queryParam("action", action)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(response -> {
                    Object val = response.get("approvalRequired");
                    return val instanceof Boolean b ? b : false;
                })
                .doOnError(e -> log.warn("Failed to check approval requirement: action={}, error={}",
                        action, e.getMessage()))
                .onErrorResume(e -> Mono.just(false));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private WebClient webClient() {
        return webClientBuilder
                .baseUrl("http://" + APPROVAL_SERVICE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Tenant-Id", TenantContext.get().getTenantId())
                .defaultHeader("X-Correlation-Id",
                        TenantContext.get().getCorrelationId() != null
                                ? TenantContext.get().getCorrelationId()
                                : "")
                .build();
    }

    @SuppressWarnings("unchecked")
    private String extractRequestId(Map response) {
        try {
            Object data = response.get("data");
            if (data instanceof Map m) {
                Object id = m.get("requestId");
                if (id != null) return id.toString();
            }
        } catch (Exception e) {
            log.warn("Could not extract requestId from approval response: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractStatus(Map response) {
        try {
            Object data = response.get("data");
            if (data instanceof Map m) {
                Object status = m.get("status");
                if (status != null) return Optional.of(status.toString());
            }
        } catch (Exception e) {
            log.warn("Could not extract status from approval response: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
