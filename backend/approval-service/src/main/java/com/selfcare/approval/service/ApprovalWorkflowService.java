package com.selfcare.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.approval.config.ApprovalServiceConfig;
import com.selfcare.approval.domain.ApprovalAction;
import com.selfcare.approval.domain.ApprovalRequest;
import com.selfcare.approval.domain.ApprovalStatus;
import com.selfcare.approval.repository.ApprovalRequestRepository;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.ConflictException;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Approval workflow service — core business logic for the four-eyes process.
 *
 * High-level flow:
 *
 *   1. SUBMIT  — calling service detects a high-risk action and calls
 *                {@link #submitForApproval}. A PENDING record is created.
 *                The calling service stores the requestId and does NOT apply
 *                the change yet.
 *
 *   2. APPROVE — approver calls {@link #approve}. The request moves to APPROVED.
 *                Calling service polls for status or is notified (via event),
 *                then applies the change.
 *
 *   3. REJECT  — approver calls {@link #reject}. The request moves to REJECTED.
 *                Calling service discards the pending change.
 *
 *   4. CANCEL  — requester calls {@link #cancel} to withdraw their own request.
 *                The request moves to CANCELLED. The change is not applied.
 *
 *   5. EXPIRE  — Scheduled job marks stale PENDING requests EXPIRED.
 *                Same effect as REJECT: the change must not be applied.
 *
 * Thread safety: all public methods are {@code @Transactional}; concurrent
 * updates to the same request are serialised by the DB row lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository repository;
    private final ApprovalServiceConfig config;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Cache key prefix for pending-approval count per tenant. */
    private static final String PENDING_COUNT_PREFIX = "selfcare:approval:pending:";

    /** The complete set of actions that always require approval. */
    private static final Set<String> REQUIRING_APPROVAL = Arrays.stream(ApprovalAction.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    // -------------------------------------------------------------------------
    // Public API — submit
    // -------------------------------------------------------------------------

    /**
     * Submit a change for four-eyes approval.
     *
     * Persists a PENDING request and returns its ID. The calling service
     * MUST NOT apply the change until the request is in APPROVED state.
     *
     * @param tenantId       Tenant context
     * @param action         One of the 9 high-risk action types (enum name)
     * @param resourceType   Logical type of the resource (e.g. "layout", "integration")
     * @param resourceId     Identifier of the specific resource
     * @param requesterId    Internal ID of the submitting admin user
     * @param requesterEmail Email of the submitting admin user
     * @param changeSnapshot Arbitrary object describing the change (serialised to JSON)
     * @param correlationId  Distributed-trace correlation ID (may be null)
     * @return The newly created approval request (in PENDING state)
     * @throws BadRequestException if the action is not a recognised high-risk type
     */
    @Transactional
    public ApprovalRequest submitForApproval(
            String tenantId,
            String action,
            String resourceType,
            String resourceId,
            String requesterId,
            String requesterEmail,
            Object changeSnapshot,
            String correlationId) {

        validateAction(action);

        String requestId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(config.getDefaultExpiryHours()));

        String snapshotJson = serialiseSnapshot(changeSnapshot);

        ApprovalRequest request = ApprovalRequest.builder()
                .requestId(requestId)
                .tenantId(tenantId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .requesterId(requesterId)
                .requesterEmail(requesterEmail)
                .changeSnapshot(snapshotJson)
                .status(ApprovalStatus.PENDING)
                .expiresAt(expiresAt)
                .createdAt(now)
                .correlationId(correlationId)
                .build();

        request = repository.save(request);
        invalidatePendingCountCache(tenantId);

        log.info("Approval request submitted: requestId={}, action={}, resource={}/{}, tenant={}, by={}",
                requestId, action, resourceType, resourceId, tenantId, requesterEmail);

        return request;
    }

    // -------------------------------------------------------------------------
    // Public API — decide
    // -------------------------------------------------------------------------

    /**
     * Approve a pending request.
     *
     * @param requestId       The UUID request identifier
     * @param approverId      Internal ID of the approving admin
     * @param approverEmail   Email of the approving admin
     * @param comments        Optional comment from the approver
     * @param ticketReference Optional change ticket reference (JIRA, etc.)
     * @throws NotFoundException  if the request does not exist
     * @throws ConflictException  if the request is not in PENDING state
     */
    @Transactional
    public void approve(String requestId, String approverId, String approverEmail,
                       String comments, String ticketReference) {
        ApprovalRequest request = loadRequest(requestId);
        requirePending(request);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setApproverId(approverId);
        request.setApproverEmail(approverEmail);
        request.setComments(comments);
        request.setTicketReference(ticketReference);
        request.setDecidedAt(Instant.now());

        repository.save(request);
        invalidatePendingCountCache(request.getTenantId());

        log.info("Approval granted: requestId={}, action={}, by={}",
                requestId, request.getAction(), approverEmail);
    }

    /**
     * Reject a pending request.
     *
     * @param requestId     The UUID request identifier
     * @param approverId    Internal ID of the rejecting admin
     * @param approverEmail Email of the rejecting admin
     * @param comments      Reason for rejection
     * @throws NotFoundException if the request does not exist
     * @throws ConflictException if the request is not in PENDING state
     */
    @Transactional
    public void reject(String requestId, String approverId, String approverEmail, String comments) {
        ApprovalRequest request = loadRequest(requestId);
        requirePending(request);

        request.setStatus(ApprovalStatus.REJECTED);
        request.setApproverId(approverId);
        request.setApproverEmail(approverEmail);
        request.setComments(comments);
        request.setDecidedAt(Instant.now());

        repository.save(request);
        invalidatePendingCountCache(request.getTenantId());

        log.info("Approval rejected: requestId={}, action={}, by={}, reason={}",
                requestId, request.getAction(), approverEmail, comments);
    }

    /**
     * Cancel a pending request (only the original requester may do this).
     *
     * @param requestId  The UUID request identifier
     * @param requesterId Internal ID of the requesting admin (must match original)
     * @param reason     Reason for cancellation
     * @throws NotFoundException   if the request does not exist
     * @throws ConflictException   if the request is not in PENDING state
     * @throws BadRequestException if the caller is not the original requester
     */
    @Transactional
    public void cancel(String requestId, String requesterId, String reason) {
        ApprovalRequest request = loadRequest(requestId);
        requirePending(request);

        if (!requesterId.equals(request.getRequesterId())) {
            throw new BadRequestException(
                    "Only the original requester can cancel this request");
        }

        request.setStatus(ApprovalStatus.CANCELLED);
        request.setComments(reason);
        request.setDecidedAt(Instant.now());

        repository.save(request);
        invalidatePendingCountCache(request.getTenantId());

        log.info("Approval cancelled: requestId={}, by={}", requestId, requesterId);
    }

    // -------------------------------------------------------------------------
    // Public API — query
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given action always requires four-eyes approval.
     *
     * Currently this is always true for all 9 high-risk actions. In a future
     * iteration this could be made configurable per tenant via the tenant
     * configuration service.
     */
    public boolean isApprovalRequired(String action) {
        return REQUIRING_APPROVAL.contains(action);
    }

    /**
     * Get all pending approval requests for a tenant.
     *
     * @param tenantId Tenant
     * @return List of PENDING requests ordered by creation time (newest first)
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> getPendingApprovals(String tenantId) {
        return repository.findByTenantIdAndStatus(tenantId, ApprovalStatus.PENDING);
    }

    /**
     * Get the full history for a tenant.
     *
     * @param tenantId Tenant
     * @param limit    Maximum number of records to return (applied)
     * @return List of requests ordered by creation time (newest first)
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> getRequestHistory(String tenantId, int limit) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit));
    }

    /**
     * Get a specific request by its UUID identifier.
     *
     * @param requestId UUID request identifier
     * @return The request, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalRequest> getRequest(String requestId) {
        return repository.findByRequestId(requestId);
    }

    /**
     * Search requests with optional filters.
     *
     * @param tenantId Filter by tenant (null = all tenants)
     * @param status   Filter by status (null = all statuses)
     * @param action   Filter by action type (null = all actions)
     * @param limit    Maximum records (default 50)
     * @return Matching requests
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> search(String tenantId, ApprovalStatus status,
                                        String action, int limit) {
        return repository.search(tenantId, status, action, PageRequest.of(0, limit));
    }

    // -------------------------------------------------------------------------
    // Scheduled jobs
    // -------------------------------------------------------------------------

    /**
     * Expire stale PENDING requests.
     *
     * Runs every {@code expiry-check-interval-ms} milliseconds (default: 5 min).
     * Picks up all PENDING requests whose expiresAt timestamp has passed.
     */
    @Scheduled(fixedRateString = "${selfcare.approval.expiry-check-interval-ms:300000}")
    @Transactional
    public void expirePendingRequests() {
        Instant now = Instant.now();
        List<ApprovalRequest> stale = repository.findStalePendingAllTenants(now);

        if (stale.isEmpty()) {
            return;
        }

        for (ApprovalRequest request : stale) {
            request.setStatus(ApprovalStatus.EXPIRED);
            request.setDecidedAt(now);
            repository.save(request);
            invalidatePendingCountCache(request.getTenantId());
            log.info("Approval expired: requestId={}, action={}, tenant={}",
                    request.getRequestId(), request.getAction(), request.getTenantId());
        }

        log.info("Expired {} stale approval requests", stale.size());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ApprovalRequest loadRequest(String requestId) {
        return repository.findByRequestId(requestId)
                .orElseThrow(() -> new NotFoundException("ApprovalRequest", requestId));
    }

    private void requirePending(ApprovalRequest request) {
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException(
                    "Approval request is not pending: " + request.getStatus());
        }
    }

    private void validateAction(String action) {
        if (!REQUIRING_APPROVAL.contains(action)) {
            throw new BadRequestException(
                    "Unknown approval action: " + action + ". Valid actions: " + REQUIRING_APPROVAL);
        }
    }

    private String serialiseSnapshot(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise change snapshot to JSON: {}", e.getMessage());
            return snapshot.toString();
        }
    }

    private void invalidatePendingCountCache(String tenantId) {
        try {
            String cacheKey = PENDING_COUNT_PREFIX + tenantId;
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to invalidate pending-count cache for tenant {}: {}",
                    tenantId, e.getMessage());
        }
    }
}
