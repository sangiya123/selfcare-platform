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
 * AI use case registry — implements the AI governance controls defined in
 * `Planning doc/06_ai/02_AI_Governance_Evaluation.md`:
 *
 *  - use-case owner
 *  - risk tier
 *  - approved models
 *  - operator/data-residency policy
 *  - tool allow list
 *  - human approval thresholds
 *  - retention policy
 *  - cost/token budget
 *  - kill switch
 *
 * Each LLM-backed capability (chat, recommendation, content rewrite, etc.)
 * is registered here. The AIModelGateway consults this table on every
 * invocation to enforce the governance policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_use_cases", indexes = {
    @Index(name = "ix_use_case_tenant", columnList = "tenant_id"),
    @Index(name = "ix_use_case_id", columnList = "use_case_id")
})
@EntityListeners(AuditingEntityListener.class)
public class AiUseCase {

    @Id
    @Column(name = "use_case_id", length = 64)
    private String useCaseId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId; // null = platform-default

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "owner", length = 128)
    private String owner;

    /**
     * Risk tier — drives which controls apply.
     *  - LOW: content draft, FAQ search, dev explanation
     *  - MEDIUM: recommendation, support summary, admin config draft
     *  - HIGH: payment/financial action prep, profile/security changes,
     *          claim decision support — requires deterministic validation
     *          and explicit approval/confirmation
     */
    @Column(name = "risk_tier", nullable = false, length = 16)
    private String riskTier;

    /** Approved model providers — comma-separated (e.g. "anthropic,openai") */
    @Column(name = "approved_providers", length = 256)
    private String approvedProviders;

    /** Default model id to use */
    @Column(name = "default_model", length = 64)
    private String defaultModel;

    /** Comma-separated list of approved tool names */
    @Column(name = "allowed_tools", length = 512)
    private String allowedTools;

    /** Whether human approval is required before output is acted on */
    @Column(name = "human_approval_required", nullable = false)
    @Builder.Default
    private Boolean humanApprovalRequired = false;

    /** Max prompt/response retention in days */
    @Column(name = "retention_days", nullable = false)
    @Builder.Default
    private Integer retentionDays = 30;

    /** Max USD budget per tenant per day; 0 = no budget */
    @Column(name = "daily_budget_usd", precision = 19, scale = 4)
    @Builder.Default
    private java.math.BigDecimal dailyBudgetUsd = java.math.BigDecimal.ZERO;

    /**
     * Regression threshold for the AI evaluation release gate.
     * If the current evaluation's task_success_rate drops by more than this
     * value vs the previous run, the result is flagged as [REGRESSION] and
     * blocks the release gate. Default 0.05 (5pp).
     */
    @Column(name = "regression_threshold")
    @Builder.Default
    private Double regressionThreshold = 0.05;

    /** Per-call max tokens */
    @Column(name = "max_tokens_per_call")
    @Builder.Default
    private Integer maxTokensPerCall = 4000;

    /** Per-user daily token limit; 0 = no limit */
    @Column(name = "user_daily_token_limit")
    @Builder.Default
    private Long userDailyTokenLimit = 0L;

    /** Data residency: IN, US, EU, ANY */
    @Column(name = "data_residency", length = 8)
    @Builder.Default
    private String dataResidency = "ANY";

    /** Enable PII mask on input (default true) */
    @Column(name = "pii_mask_input", nullable = false)
    @Builder.Default
    private Boolean piiMaskInput = true;

    /** Enable PII mask on output (default true) */
    @Column(name = "pii_mask_output", nullable = false)
    @Builder.Default
    private Boolean piiMaskOutput = true;

    /** Whether this use case is currently enabled */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** Optional notes / approval reference */
    @Column(name = "approval_reference", length = 128)
    private String approvalReference;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
