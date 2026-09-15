package com.selfcare.audit.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * An immutable audit event.
 *
 * Stored in an append-only MySQL table — no UPDATE, no DELETE.
 * Once written, the event is permanent.
 *
 * Records login/logout, config publish/rollback, role changes, payment state
 * transitions, high-risk AI actions, security events, and admin exports.
 *
 * @see com.selfcare.audit.service.AuditService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "ix_audit_tenant", columnList = "tenant_id"),
    @Index(name = "ix_audit_user", columnList = "user_id"),
    @Index(name = "ix_audit_action", columnList = "action"),
    @Index(name = "ix_audit_resource", columnList = "resource_type, resource_id"),
    @Index(name = "ix_audit_occurred", columnList = "occurred_at")
})
@EntityListeners(AuditingEntityListener.class)
public class AuditEvent {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** Tenant this event belongs to. */
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /** User who performed the action. May be "system" for automated events. */
    @Column(name = "user_id", length = 64)
    private String userId;

    /** Session ID, if applicable. */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /**
     * The action performed.
     * e.g. "LOGIN", "LOGOUT", "CONFIG_PUBLISH", "ROLE_CHANGED", "PAYMENT_INITIATED"
     */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** Type of resource affected. e.g. "CONFIG", "ROLE", "PAYMENT" */
    @Column(name = "resource_type", length = 64)
    private String resourceType;

    /** ID of the resource affected. */
    @Column(name = "resource_id", length = 128)
    private String resourceId;

    /** State of the resource before the action. JSON. */
    @Lob
    @Column(name = "before_state")
    private String beforeState;

    /** State of the resource after the action. JSON. */
    @Lob
    @Column(name = "after_state")
    private String afterState;

    /**
     * Correlation ID — links this event to the originating request,
     * enabling end-to-end tracing.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /** IP address of the actor. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** User agent of the actor. */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Severity / outcome. e.g. "SUCCESS", "FAILURE", "WARNING" */
    @Column(name = "severity", length = 16)
    private String severity;

    /** Free-form message for context. */
    @Column(name = "message", length = 1024)
    private String message;

    /** When the event actually occurred (from the originating system). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public enum Severity {
        SUCCESS,
        FAILURE,
        WARNING,
        INFO
    }
}
