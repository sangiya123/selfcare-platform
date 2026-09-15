package com.selfcare.approval.web.dto;

import com.selfcare.approval.domain.ApprovalStatus;

import java.time.Instant;

/**
 * DTO returned to API consumers for an approval request.
 *
 * {@code changeSnapshot} is deserialized to an Object (typically a Map)
 * so JSON structure is preserved without a strongly-typed schema. Each
 * caller knows its own change shape (layout diff, role diff, etc.).
 */
public record ApprovalRequestDto(
        String requestId,
        String tenantId,
        String action,
        String resourceType,
        String resourceId,
        String requesterId,
        String requesterEmail,
        Object changeSnapshot,
        String approverId,
        String approverEmail,
        ApprovalStatus status,
        String comments,
        Instant expiresAt,
        Instant decidedAt,
        Instant createdAt,
        String correlationId,
        String ticketReference
) {}
