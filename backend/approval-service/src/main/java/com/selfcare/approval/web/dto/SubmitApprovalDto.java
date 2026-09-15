package com.selfcare.approval.web.dto;

/**
 * Body for submitting a new approval request.
 *
 * Used by:
 *   POST /api/v1/admin/approvals
 *
 * The tenant ID and requester are taken from the authenticated session
 * (TenantContext) — they are not part of the body to prevent impersonation.
 *
 * {@code changeSnapshot} is a free-form JSON object describing the change.
 * The shape is caller-specific:
 *   - LAYOUT_PUBLISH: { "before": {...layout...}, "after": {...layout...} }
 *   - ROLE_PRIVILEGE_ESCALATION: { "userId": "...", "before": "VIEWER", "after": "ADMIN" }
 *   - etc.
 */
public record SubmitApprovalDto(
        String action,
        String resourceType,
        String resourceId,
        Object changeSnapshot
) {}
