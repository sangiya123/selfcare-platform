package com.omobio.config.web;

import com.omobio.config.domain.LayoutDocument;
import com.omobio.config.service.ApprovalClient;
import com.omobio.config.service.LayoutService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.ConflictException;
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
 * Admin REST API for layout documents.
 *
 * Endpoints:
 *   GET    /api/v1/admin/pages?tenantId=...         — List layouts
 *   GET    /api/v1/admin/pages/{id}                — Get one
 *   POST   /api/v1/admin/pages                    — Save (DRAFT)
 *   POST   /api/v1/admin/pages/{id}/submit-approval — Submit for approval (async)
 *   POST   /api/v1/admin/pages/{id}/publish       — Publish (sync, four-eyes gated)
 *   POST   /api/v1/admin/pages/{id}/publish-status/{requestId} — Check approval status
 *   POST   /api/v1/admin/pages/{id}/archive       — Archive
 *   DELETE /api/v1/admin/pages/{id}               — Delete
 *
 * Approval flow:
 *   - {@code /submit-approval} submits the layout for four-eyes review.
 *     Returns immediately with a requestId. The admin should poll
 *     {@code /publish-status/{requestId}} or wait for a notification.
 *   - {@code /publish} is a synchronous convenience that blocks until
 *     an approver has decided. Use the async path for better UX.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/pages")
@RequiredArgsConstructor
@Tag(name = "Admin Pages", description = "Manage page layouts (with four-eyes approval for publish)")
public class AdminLayoutController {

    private final LayoutService service;
    private final ApprovalClient approvalClient;

    @GetMapping
    @Operation(summary = "List layouts for a tenant")
    public ResponseEntity<ApiResponse<List<LayoutDocument>>> list(@RequestParam String tenantId) {
        return ResponseEntity.ok(ApiResponse.of(service.listForTenant(tenantId), ""));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get layout by ID")
    public ResponseEntity<ApiResponse<LayoutDocument>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id), ""));
    }

    @PostMapping
    @Operation(summary = "Save layout (creates DRAFT or updates existing)")
    public ResponseEntity<ApiResponse<LayoutDocument>> save(@RequestBody LayoutDocument layout) {
        return ResponseEntity.ok(ApiResponse.of(service.save(layout), ""));
    }

    /**
     * Submit a layout publish for four-eyes approval (async path).
     *
     * The layout is NOT published yet. Callers should poll
     * {@code /publish-status/{requestId}} or wait for a webhook notification.
     */
    @PostMapping("/{id}/submit-approval")
    @Operation(summary = "Submit layout publish for four-eyes approval",
            description = "Submits the layout for approval. Returns a requestId. " +
                    "Poll /publish-status/{requestId} or wait for a notification.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitForApproval(@PathVariable String id) {
        LayoutDocument doc = service.get(id);

        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        java.util.Map<String, Object> snapshot = java.util.Map.of(
                "layoutId", doc.getId(),
                "experience", doc.getExperience(),
                "environment", doc.getEnvironment(),
                "profileKey", doc.getProfileKey(),
                "currentVersion", doc.getConfigVersion(),
                "currentStatus", doc.getStatus()
        );

        String requestId = approvalClient.submitForApproval(
                        tenantId, "LAYOUT_PUBLISH", "layout",
                        doc.getId(), userId, userId, snapshot)
                .block(java.time.Duration.ofSeconds(10));

        if (requestId == null) {
            throw new ConflictException("LAYOUT_PUBLISH",
                    "Could not submit for approval — approval-service unavailable");
        }

        log.info("Layout approval submitted: layoutId={}, requestId={}", doc.getId(), requestId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(Map.of(
                        "requestId", requestId,
                        "status", "PENDING",
                        "message", "Layout publish submitted for four-eyes approval"
                )));
    }

    /**
     * Check the approval status of a publish request.
     */
    @GetMapping("/{id}/publish-status/{requestId}")
    @Operation(summary = "Check layout publish approval status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishStatus(
            @PathVariable String id, @PathVariable String requestId) {

        var status = approvalClient.getRequestStatus(requestId)
                .block(java.time.Duration.ofSeconds(5));

        if (status == null || status.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.of(Map.of(
                    "requestId", requestId,
                    "status", "UNKNOWN",
                    "message", "Could not retrieve status from approval-service"
            )));
        }

        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "requestId", requestId,
                "status", status.get()
        )));
    }

    /**
     * Publish a layout (synchronous, four-eyes gated).
     *
     * This endpoint blocks until an approver has decided. For production,
     * prefer the async path: {@code /submit-approval}.
     *
     * @throws ConflictException if the approval is rejected, cancelled, or expired
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish layout (synchronous, four-eyes gated)")
    public ResponseEntity<ApiResponse<LayoutDocument>> publish(@PathVariable String id) {
        LayoutDocument published = service.publish(id);
        return ResponseEntity.ok(ApiResponse.of(published));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive layout")
    public ResponseEntity<ApiResponse<LayoutDocument>> archive(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.archive(id), ""));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete layout")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }
}
