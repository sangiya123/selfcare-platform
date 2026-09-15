package com.selfcare.approval.web;

import com.selfcare.approval.domain.ApprovalRequest;
import com.selfcare.approval.domain.ApprovalStatus;
import com.selfcare.approval.service.ApprovalWorkflowService;
import com.selfcare.approval.web.dto.ApprovalDecisionDto;
import com.selfcare.approval.web.dto.ApprovalRequestDto;
import com.selfcare.approval.web.dto.SubmitApprovalDto;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the four-eyes approval workflow.
 *
 * Endpoints (all under {@code /api/v1/admin/approvals}):
 *   GET    /                       List requests (filter by status, action, tenant)
 *   POST   /                       Submit new approval request
 *   GET    /{requestId}            Get a specific request
 *   POST   /{requestId}/approve    Approve a pending request
 *   POST   /{requestId}/reject     Reject a pending request
 *   POST   /{requestId}/cancel     Cancel own pending request
 *   GET    /pending                List PENDING requests for the current tenant
 *   GET    /history                History of decisions for the current tenant
 *   GET    /check                  Check if an action requires approval
 *   GET    /actions                List the 9 high-risk action types
 *
 * Tenant ID is always taken from the authenticated session (TenantContext),
 * never from the body or query string, to prevent tenant impersonation.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Four-eyes approval workflow for high-risk admin actions")
public class ApprovalController {

    private final ApprovalWorkflowService service;

    /**
     * List approval requests with optional filters.
     */
    @GetMapping
    @Operation(summary = "List approval requests",
            description = "List requests for the current tenant. Supports filter by status and action.")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDto>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "50") int limit) {

        String tenantId = TenantContext.get().getTenantId();
        ApprovalStatus statusFilter = parseStatus(status);
        List<ApprovalRequest> requests = service.search(tenantId, statusFilter, action, limit);
        return ResponseEntity.ok(ApiResponse.of(toDto(requests)));
    }

    /**
     * Submit a new approval request.
     */
    @PostMapping
    @Operation(summary = "Submit approval request",
            description = "Submit a high-risk action for four-eyes approval. Returns the new requestId.")
    public ResponseEntity<ApiResponse<ApprovalRequestDto>> submit(
            @RequestBody SubmitApprovalDto body) {

        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();
        String email = currentUserEmail();
        String correlationId = TenantContext.get().getCorrelationId();

        ApprovalRequest request = service.submitForApproval(
                tenantId,
                body.action(),
                body.resourceType(),
                body.resourceId(),
                userId,
                email,
                body.changeSnapshot(),
                correlationId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(toDto(request)));
    }

    /**
     * Get a specific approval request.
     */
    @GetMapping("/{requestId}")
    @Operation(summary = "Get approval request by ID")
    public ResponseEntity<ApiResponse<ApprovalRequestDto>> get(@PathVariable String requestId) {
        ApprovalRequest request = service.getRequest(requestId)
                .orElseThrow(() -> new com.selfcare.platform.common.web.NotFoundException(
                        "ApprovalRequest", requestId));
        return ResponseEntity.ok(ApiResponse.of(toDto(request)));
    }

    /**
     * Approve a pending request.
     */
    @PostMapping("/{requestId}/approve")
    @Operation(summary = "Approve a pending request")
    public ResponseEntity<ApiResponse<ApprovalRequestDto>> approve(
            @PathVariable String requestId,
            @RequestBody(required = false) ApprovalDecisionDto body) {

        String userId = TenantContext.get().getUserId();
        String email = currentUserEmail();

        ApprovalDecisionDto payload = body != null
                ? body
                : new ApprovalDecisionDto(null, null);

        service.approve(requestId, userId, email, payload.comments(), payload.ticketReference());

        ApprovalRequest updated = service.getRequest(requestId).orElseThrow();
        return ResponseEntity.ok(ApiResponse.of(toDto(updated)));
    }

    /**
     * Reject a pending request.
     */
    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject a pending request")
    public ResponseEntity<ApiResponse<ApprovalRequestDto>> reject(
            @PathVariable String requestId,
            @RequestBody(required = false) ApprovalDecisionDto body) {

        String userId = TenantContext.get().getUserId();
        String email = currentUserEmail();

        ApprovalDecisionDto payload = body != null
                ? body
                : new ApprovalDecisionDto(null, null);

        service.reject(requestId, userId, email, payload.comments());

        ApprovalRequest updated = service.getRequest(requestId).orElseThrow();
        return ResponseEntity.ok(ApiResponse.of(toDto(updated)));
    }

    /**
     * Cancel own pending request.
     */
    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "Cancel own pending request")
    public ResponseEntity<ApiResponse<ApprovalRequestDto>> cancel(
            @PathVariable String requestId,
            @RequestBody(required = false) ApprovalDecisionDto body) {

        String userId = TenantContext.get().getUserId();

        ApprovalDecisionDto payload = body != null
                ? body
                : new ApprovalDecisionDto(null, null);

        service.cancel(requestId, userId, payload.comments());

        ApprovalRequest updated = service.getRequest(requestId).orElseThrow();
        return ResponseEntity.ok(ApiResponse.of(toDto(updated)));
    }

    /**
     * List pending requests for the current tenant.
     */
    @GetMapping("/pending")
    @Operation(summary = "List PENDING approval requests for the current tenant")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDto>>> pending() {
        String tenantId = TenantContext.get().getTenantId();
        List<ApprovalRequest> pending = service.getPendingApprovals(tenantId);
        return ResponseEntity.ok(ApiResponse.of(toDto(pending)));
    }

    /**
     * List recent history for the current tenant.
     */
    @GetMapping("/history")
    @Operation(summary = "List recent approval history for the current tenant")
    public ResponseEntity<ApiResponse<List<ApprovalRequestDto>>> history(
            @RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.get().getTenantId();
        List<ApprovalRequest> history = service.getRequestHistory(tenantId, limit);
        return ResponseEntity.ok(ApiResponse.of(toDto(history)));
    }

    /**
     * Check whether an action requires approval.
     */
    @GetMapping("/check")
    @Operation(summary = "Check if an action requires approval")
    public ResponseEntity<ApiResponse<Map<String, Object>>> check(@RequestParam String action) {
        boolean required = service.isApprovalRequired(action);
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "action", action,
                "approvalRequired", required
        )));
    }

    /**
     * List the 9 high-risk action types.
     */
    @GetMapping("/actions")
    @Operation(summary = "List high-risk action types requiring approval")
    public ResponseEntity<ApiResponse<List<String>>> actions() {
        List<String> names = java.util.Arrays.stream(
                        com.selfcare.approval.domain.ApprovalAction.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(names));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static ApprovalStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ApprovalStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.selfcare.platform.common.web.BadRequestException(
                    "Unknown status: " + status);
        }
    }

    /**
     * Best-effort extraction of the current user's email from the tenant context.
     * Falls back to the userId if no email is set in the context.
     */
    private String currentUserEmail() {
        TenantContext ctx = TenantContext.get();
        if (ctx == null) {
            return "UNKNOWN";
        }
        return ctx.getUserId() != null ? ctx.getUserId() : "UNKNOWN";
    }

    private static List<ApprovalRequestDto> toDto(List<ApprovalRequest> requests) {
        return requests.stream().map(ApprovalController::toDto).toList();
    }

    private static ApprovalRequestDto toDto(ApprovalRequest r) {
        return new ApprovalRequestDto(
                r.getRequestId(),
                r.getTenantId(),
                r.getAction(),
                r.getResourceType(),
                r.getResourceId(),
                r.getRequesterId(),
                r.getRequesterEmail(),
                r.getChangeSnapshot(),
                r.getApproverId(),
                r.getApproverEmail(),
                r.getStatus(),
                r.getComments(),
                r.getExpiresAt(),
                r.getDecidedAt(),
                r.getCreatedAt(),
                r.getCorrelationId(),
                r.getTicketReference()
        );
    }
}
