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
 * Connection — one phone number, broadband line, DTV subscription, etc. that
 * is linked to an account.
 *
 * Source of truth model (Doc 1 sec 6):
 *   selfcare_connection
 *   - account_id
 *   - connection_id
 *   - number/display_id
 *   - lob
 *   - connection_type
 *   - relationship/status
 *   - is_primary
 *   - linked_at
 *   - updated_at
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "connections", indexes = {
    @Index(name = "ix_connection_account", columnList = "account_id"),
    @Index(name = "ix_connection_number", columnList = "number")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_connection_tenant_number",
                     columnNames = {"tenant_id", "number"})
})
@EntityListeners(AuditingEntityListener.class)
public class Connection {

    @Id
    @Column(name = "connection_id", length = 64)
    private String connectionId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /** MSISDN, account number, or other display ID */
    @Column(name = "number", nullable = false, length = 32)
    private String number;

    /** LOB: MOBILE, BB, DTV, FIBRE */
    @Column(name = "lob", nullable = false, length = 16)
    private String lob;

    /** Connection type: prepaid, postpaid, hybrid */
    @Column(name = "connection_type", length = 16)
    private String connectionType;

    /** Relationship: PRIMARY, LINKED */
    @Column(name = "relationship", nullable = false, length = 16)
    private String relationship;

    /** Status: ACTIVE, SUSPENDED, DISCONNECTED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "display_name", length = 64)
    private String displayName;

    /** Is this the primary connection? */
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @Column(name = "linked_at")
    private Instant linkedAt;

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