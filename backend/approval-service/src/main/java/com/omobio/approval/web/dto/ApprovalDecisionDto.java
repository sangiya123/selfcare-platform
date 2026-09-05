package com.omobio.approval.web.dto;

/**
 * Body for an approve or reject endpoint.
 *
 * Used by:
 *   POST /api/v1/admin/approvals/{requestId}/approve
 *   POST /api/v1/admin/approvals/{requestId}/reject
 *
 * {@code ticketReference} is optional; it lets the approver link the
 * decision to a change ticket (JIRA, etc.) so the resulting change can
 * be tracked in the platform's audit + ticketing systems.
 */
public record ApprovalDecisionDto(
        String comments,
        String ticketReference
) {}
