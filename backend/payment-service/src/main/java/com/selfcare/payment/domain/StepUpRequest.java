package com.selfcare.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Step-up authentication request — for high-risk actions.
 * Created when a client requests step-up; verified with the OTP code;
 * then a step-up token is issued for the specific action.
 */
@Entity
@Table(name = "step_up_request", indexes = {
    @Index(name = "idx_stepup_correlation", columnList = "correlationId", unique = true),
    @Index(name = "idx_stepup_tenant_user", columnList = "tenantId,userId"),
    @Index(name = "idx_stepup_status", columnList = "status")
})
public class StepUpRequest {

    @Id
    @Column(length = 64)
    private String correlationId;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 100)
    private String idempotencyKey;

    @Column(length = 256)
    private String codeHash;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, VERIFIED, EXPIRED, LOCKED

    @Column
    private Integer attemptCount;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant verifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
