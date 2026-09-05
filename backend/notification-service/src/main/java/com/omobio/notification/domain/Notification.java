package com.omobio.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Notification record — durable log of every send attempt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "ix_notif_tenant_user", columnList = "tenant_id, user_id"),
    @Index(name = "ix_notif_status", columnList = "status"),
    @Index(name = "ix_notif_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @Column(name = "notification_id", length = 64)
    private String notificationId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "user_id", length = 64)
    private String userId;

    /** Channel: PUSH, SMS, EMAIL, IN_APP */
    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    /** Template ID (e.g., payment_success, bill_due) */
    @Column(name = "template_id", length = 64)
    private String templateId;

    @Column(name = "recipient", length = 256)
    private String recipient;

    @Column(name = "subject", length = 256)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /** Status: QUEUED, SENT, DELIVERED, FAILED, OPTED_OUT */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @Column(name = "locale", length = 8)
    private String locale;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}