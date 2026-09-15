package com.selfcare.identity.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * One-time password (OTP) code for customer authentication.
 *
 * <p>Lifecycle: ACTIVE -> USED (on successful verify) or ACTIVE -> EXPIRED
 * (TTL elapsed / max-attempts reached). Codes are stored hashed, never in plain
 * text. The raw code is only held in memory at dispatch time and is sent to
 * the user via the channel provider (SMS/EMAIL/PUSH).
 *
 * <p>The {@code correlationId} links the verify attempt back to the original
 * generate call so the client can correlate request/response even though the
 * raw code is opaque.
 *
 * <p>Status values: ACTIVE, USED, EXPIRED, LOCKED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "otp_codes", indexes = {
    @Index(name = "ix_otp_tenant_identifier", columnList = "tenant_id, identifier"),
    @Index(name = "ix_otp_correlation", columnList = "correlation_id"),
    @Index(name = "ix_otp_status_expires", columnList = "status, expires_at")
})
@EntityListeners(AuditingEntityListener.class)
public class OtpCode {

    @Id
    @Column(name = "otp_id", length = 64)
    private String otpId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /**
     * Recipient identifier — MSISDN for SMS, email address for EMAIL,
     * customer ID for PUSH (push tokens are looked up by customer ID).
     */
    @Column(name = "identifier", nullable = false, length = 128)
    private String identifier;

    /**
     * Hashed OTP code (SHA-256). The plain code is never persisted.
     */
    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    /**
     * Channel: SMS, EMAIL, PUSH.
     */
    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    /**
     * Status: ACTIVE, USED, EXPIRED, LOCKED.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /**
     * Number of failed verification attempts. When this reaches
     * the configured max (default 5), the code is locked.
     */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Correlation ID linking generate/verify pairs. Echoed to the client on
     * generate so the verify call can carry the same value.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /**
     * Status constants. Centralised here so the entity owns its own vocabulary.
     */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_LOCKED = "LOCKED";

    /**
     * Channel constants.
     */
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_PUSH = "PUSH";
}
