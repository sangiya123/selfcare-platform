package com.omobio.admin.service;

import com.omobio.platform.common.tenant.TenantContext;
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
 * Used by the admin user / role flow to gate privilege-escalation changes
 * behind the four-eyes approval workflow.
 *
 * Failure handling:
 *   - Timeout / connection error: Mono.empty() (caller decides how to handle)
 *   - 4xx: logged and Mono.empty() (treat as transient)
 *   - 5xx: logged and Mono.empty() (treat as transient)
 *
 * For a more robust implementation, add Resilience4j circuit breaker
 * (the platform already uses it in dashboard-bff and ai-gateway).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalClient {

    private final WebClient.Builder webClientBuilder;

    private static final String APPROVAL_SERVICE = "approval-service";

    @Value("${approval.client.timeout-ms:5000}")
    private int timeoutMs;

    /**
     * Submit a privilege-escalation change for four-eyes approval.
     *
     * @param tenantId       Tenant context
     * @param action         ApprovalAction enum name (e.g. "ROLE_PRIVILEGE_ESCALATION")
     * @param resourceType   Logical type (e.g. "admin_user")
     * @param resourceId     Identifier of the resource (e.g. user UUID)
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
                .map(this::extractRequestId)
                .doOnSuccess(id -> log.info("Approval submitted: requestId={}, action={}, resource={}/{}",
                        id, action, resourceType, resourceId))
                .doOnError(e -> log.error("Failed to submit approval: action={}, resource={}/{}, error={}",
                        action, resourceType, resourceId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Get the current status of an approval request.
     */
    public Mono<Optional<String>> getRequestStatus(String requestId) {
        return webClient()
                .get()
                .uri("/api/v1/admin/approvals/{requestId}", requestId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(this::extractStatus)
                .doOnError(e -> log.warn("Failed to get approval status: requestId={}, error={}",
                        requestId, e.getMessage()))
                .onErrorResume(e -> Mono.just(Optional.empty()));
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
