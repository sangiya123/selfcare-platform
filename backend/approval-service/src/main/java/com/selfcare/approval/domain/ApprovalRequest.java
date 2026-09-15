package com.selfcare.approval.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Approval request — durable record of a four-eyes approval workflow.
 *
 * Lifecycle:
 *   1. Requester submits a change for approval (PENDING)
 *   2. An approver reviews and decides (APPROVED / REJECTED / CANCELLED)
 *   3. Scheduled job expires stale PENDING requests (EXPIRED)
 *
 * The changeSnapshot JSON field stores the before/after diff so the
 * approver can understand exactly what will change if approved.
 *
 * Design notes:
 * - requestId is a UUID string (unique, non-sequential — avoids information
 *   leakage about request volume).
 * - All timestamps are UTC (Instant).
 * - approverId / approverEmail are null until a decision is made.
 * - The entity is immutable after creation — only status, approver fields,
 *   comments, and decidedAt change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_requests", indexes = {
    @Index(name = "ix_approval_status", columnList = "status"),
    @Index(name = "ix_approval_action", columnList = "action"),
    @Index(name = "ix_approval_tenant", columnList = "tenant_id"),
    @Index(name = "ix_approval_created", columnList = "created_at"),
    @Index(name = "ix_approval_requester", columnList = "requester_id"),
    @Index(name = "ix_approval_approver", columnList = "approver_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID string, externally visible request identifier. */
    @Column(name = "request_id", nullable = false, length = 64, unique = true)
    private String requestId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /** ApprovalAction enum name, e.g. "LAYOUT_PUBLISH". */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** Logical resource type being changed, e.g. "layout", "integration", "feature_flag". */
    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    /** Identifier of the specific resource, e.g. layout UUID or integration name. */
    @Column(name = "resource_id", nullable = false, length = 64)
    private String resourceId;

    // ---- Requester info ----

    /** Internal user ID of the person who submitted this request. */
    @Column(name = "requester_id", nullable = false, length = 64)
    private String requesterId;

    @Column(name = "requester_email", nullable = false, length = 256)
    private String requesterEmail;

    // ---- Change details ----

    /**
     * JSON snapshot of the change: before/after diff, or a summary description.
     * Stored as TEXT — the JSON content is validated by the submitting service.
     */
    @Column(name = "change_snapshot", columnDefinition = "TEXT")
    private String changeSnapshot;

    // ---- Approval info ----

    /** Filled when an approver makes a decision. */
    @Column(name = "approver_id", length = 64)
    private String approverId;

    @Column(name = "approver_email", length = 256)
    private String approverEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApprovalStatus status;

    /** Optional comment from the approver or requester (rejection reason, cancellation reason). */
    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    /** When this request expires if not decided. Null = use default. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** When a final decision was made. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ---- Metadata ----

    /** Correlation ID from the originating request for distributed tracing. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /**
     * Optional external ticket reference (JIRA issue, change ticket, etc.)
     * Populated when the approval is approved so the change can be tracked
     * in the ticketing system.
     */
    @Column(name = "ticket_reference", length = 128)
    private String ticketReference;
}
