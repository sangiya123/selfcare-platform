package com.omobio.admin.domain;

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
 * Admin session — durable record of an authenticated admin session.
 *
 * Mirrors the pattern from {@link com.omobio.identity.domain.Session} but
 * for internal admin users.
 *
 * Stored in MySQL InnoDB for durability. Hot session data in Redis AUTH.
 *
 * @see AdminUser
 * @see com.omobio.admin.service.AdminSessionService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admin_sessions", indexes = {
    @Index(name = "ix_admin_session_user", columnList = "admin_user_id"),
    @Index(name = "ix_admin_session_token_family", columnList = "token_family_id"),
    @Index(name = "ix_admin_session_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
public class AdminSession {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** The admin user who owns this session. */
    @Column(name = "admin_user_id", nullable = false, length = 64)
    private String adminUserId;

    /** The tenant this session is scoped to. */
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    /** Role held by the user at session creation. */
    @Column(name = "role", length = 32)
    private String role;

    /** Token family for refresh token rotation. */
    @Column(name = "token_family_id", length = 64)
    private String tokenFamilyId;

    /** Current refresh token hash. */
    @Column(name = "current_token_hash", length = 128)
    private String currentTokenHash;

    /** Hash of the previous refresh token (for rotation replay detection). */
    @Column(name = "previous_token_hash", length = 128)
    private String previousTokenHash;

    /** Device identifier. */
    @Column(name = "device_id", length = 128)
    private String deviceId;

    /** Status: ACTIVE, EXPIRED, REVOKED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

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

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
