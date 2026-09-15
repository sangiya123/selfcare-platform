package com.selfcare.ai.domain;

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
 * AI kill switch — per the AI governance and security docs:
 *
 *  - "incident/kill switch" (governance)
 *  - "model/provider allow list and emergency disable switch" (security)
 *
 * When active=true, the AIModelGateway short-circuits the use case
 * and returns the fallback response. Activating a kill switch should
 * be auditable (recorded in the audit service) and reversible.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_kill_switches", indexes = {
    @Index(name = "ix_ks_tenant_usecase", columnList = "tenant_id, use_case_id"),
    @Index(name = "ix_ks_active", columnList = "active")
})
@EntityListeners(AuditingEntityListener.class)
public class AiKillSwitch {

    @Id
    @Column(name = "kill_switch_id", length = 64)
    private String killSwitchId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId; // null = all tenants

    @Column(name = "use_case_id", length = 64)
    private String useCaseId; // null = all use cases for tenant

    /** "USE_CASE" or "PROVIDER" — scope of the kill */
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    /** Provider name when scope=PROVIDER (e.g. "anthropic", "openai") */
    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "reason", length = 1024)
    private String reason;

    @Column(name = "activated_by", length = 64)
    private String activatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt; // null = until manually deactivated

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
