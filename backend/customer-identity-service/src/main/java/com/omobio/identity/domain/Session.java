package com.omobio.identity.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Customer session — durable record of an authenticated user session.
 *
 * Stored in MySQL InnoDB for durability. Hot cache in Redis AUTH for fast access.
 *
 * Session lifecycle:
 * 1. Created on successful login
 * 2. Refreshed when access token is renewed
 * 3. Revoked on logout, security event, or admin action
 * 4. Expired after configured TTL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customer_sessions", indexes = {
    @Index(name = "ix_session_tenant_user", columnList = "tenant_id, user_id"),
    @Index(name = "ix_session_token_family", columnList = "token_family_id"),
    @Index(name = "ix_session_device", columnList = "device_id")
})
@EntityListeners(AuditingEntityListener.class)
public class Session {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Token family for refresh token rotation. All tokens in a family share this ID. */
    @Column(name = "token_family_id", length = 64)
    private String tokenFamilyId;

    /** Device identifier (mobile device ID, browser fingerprint) */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** Device description: "iPhone 15 Pro", "Chrome 120" */
    @Column(name = "device_description", length = 256)
    private String deviceDescription;

    /** IP address at session creation */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Status: ACTIVE, EXPIRED, REVOKED, REPLACED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Current refresh token hash */
    @Column(name = "current_token_hash", length = 128)
    private String currentTokenHash;

    /** Hash of the previous refresh token (for rotation replay detection) */
    @Column(name = "previous_token_hash", length = 128)
    private String previousTokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 64)
    private String revokedReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "opt_lock_version")
    private Long optLockVersion;
}