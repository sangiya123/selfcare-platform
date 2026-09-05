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
 * ConsentRecord — Immutable record of a user's consent decision.
 *
 * Stored in MySQL (JPA). Never deleted, only superseded by a new
 * record (e.g. when the policy version is updated).
 *
 * Used for:
 *   - GDPR Article 7 (clear affirmative action) audit trail
 *   - Proving the user consented to a specific version of the policy
 *   - Marketing opt-in / opt-out decisions
 *   - AI processing consent
 */
@Entity
@Table(name = "consent_records", indexes = {
    @Index(name = "ix_consent_user", columnList = "tenantId,userId,purpose"),
    @Index(name = "ix_consent_active", columnList = "tenantId,userId,purpose,supersededAt")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String userId;

    /**
     * One of: TERMS_OF_SERVICE, PRIVACY_POLICY, MARKETING_EMAIL,
     * MARKETING_SMS, MARKETING_PUSH, ANALYTICS, PERSONALIZATION,
     * THIRD_PARTY_SHARING, AI_PROCESSING, BIOMETRIC_AUTH,
     * LOCATION_TRACKING, ...
     */
    @Column(nullable = false, length = 64)
    private String purpose;

    /**
     * Version of the policy / terms the user consented to.
     */
    @Column(nullable = false, length = 32)
    private String version;

    @Column(nullable = false)
    private boolean granted;

    /**
     * Free-form source (e.g. "signup-screen", "settings-screen",
     * "renewal-prompt") for proving clear affirmative action.
     */
    @Column(length = 128)
    private String source;

    /**
     * IP address of the user at the time of consent — for legal audit.
     */
    @Column(length = 64)
    private String ipAddress;

    /**
     * User-Agent at the time of consent.
     */
    @Column(length = 256)
    private String userAgent;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant capturedAt;

    /**
     * When this record is superseded by a later decision for the same
     * (tenantId, userId, purpose) tuple, this field is set to
     * {@code capturedAt} of the new record.
     */
    @Column
    private Instant supersededAt;
}
