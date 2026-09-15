package com.selfcare.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Step-up verification audit record. Append-only.
 * Records that a step-up was successfully completed (for audit, not for retry).
 */
@Entity
@Table(name = "step_up_verification", indexes = {
    @Index(name = "idx_stepup_verify_correlation", columnList = "correlationId"),
    @Index(name = "idx_stepup_verify_tenant", columnList = "tenantId"),
    @Index(name = "idx_stepup_verify_user", columnList = "userId")
})
public class StepUpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String correlationId;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false)
    private Instant verifiedAt;

    public Long getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
