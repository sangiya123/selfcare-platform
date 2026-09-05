package com.omobio.identity.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * DataErasureRequest — Article 17 (Right to be forgotten) audit record.
 *
 * Even after the user's PII is wiped from operational stores, we
 * retain this anonymized record (only the user ID and timestamps)
 * to:
 *   1. Prevent re-registration of the same primary identity
 *   2. Maintain a record of the erasure for regulatory audits
 *   3. Honour the "do not contact again" obligation
 *
 * The user ID is hashed; the original is not recoverable.
 */
@Entity
@Table(name = "data_erasure_requests", indexes = {
    @Index(name = "ix_erasure_user_hash", columnList = "tenantId,userIdHash"),
    @Index(name = "ix_erasure_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataErasureRequest {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    /**
     * Hashed user identifier (e.g. SHA-256(msisdn)).
     * The original is not stored.
     */
    @Column(nullable = false, length = 128)
    private String userIdHash;

    /**
     * PENDING, IN_PROGRESS, COMPLETED, FAILED
     */
    @Column(nullable = false, length = 32)
    private String status;

    @Column
    private String reason;

    @Column
    private String requestedBy;  // user / admin / regulator

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @Column(length = 1024)
    private String failureReason;

    /**
     * List of downstream services that were notified to perform their
     * own data erasure (e.g. notification-service, audit-service).
     */
    @Column(length = 1024)
    private String notifiedServices;
}
