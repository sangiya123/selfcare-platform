package com.omobio.account.domain;

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
 * Account master record.
 *
 * One account per primary identity (e.g., Dialog primary MSISDN).
 * May have multiple linked connections (mobile, BB, DTV, fibre).
 *
 * Source of truth model (Doc 1 sec 6 + Doc 4):
 *   selfcare_account
 *   - account_id
 *   - primary_identity (MSISDN for telco)
 *   - profile_version
 *   - status
 *   - last_login_at
 *   - updated_at
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_account_tenant_primary",
                     columnNames = {"tenant_id", "primary_identity"})
})
@EntityListeners(AuditingEntityListener.class)
public class Account {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /** Primary identity (MSISDN for telco, customer ID for insurance/travel) */
    @Column(name = "primary_identity", nullable = false, length = 32)
    private String primaryIdentity;

    /** Profile version from source system */
    @Column(name = "profile_version")
    private Long profileVersion;

    /** Account status: ACTIVE, SUSPENDED, CLOSED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "email", length = 128)
    private String email;

    /** Operator-specific fields as JSON */
    @Column(name = "operator_attributes", columnDefinition = "JSON")
    private String operatorAttributes;

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