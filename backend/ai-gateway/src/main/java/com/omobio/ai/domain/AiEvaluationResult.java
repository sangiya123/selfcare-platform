package com.omobio.ai.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * AI evaluation result — per the AI governance release gate:
 * "Every AI use case ships with a versioned evaluation set, baseline score,
 * regression threshold and red-team cases. A model/provider change is
 * treated like a software release and may be rolled back independently."
 *
 * Tracks a single evaluation run against a use case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_evaluation_results", indexes = {
    @Index(name = "ix_eval_tenant_usecase", columnList = "tenant_id, use_case_id"),
    @Index(name = "ix_eval_run", columnList = "use_case_id, evaluation_set_version, run_at")
})
@EntityListeners(AuditingEntityListener.class)
public class AiEvaluationResult {

    @Id
    @Column(name = "evaluation_result_id", length = 64)
    private String evaluationResultId;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "use_case_id", nullable = false, length = 64)
    private String useCaseId;

    @Column(name = "evaluation_set_version", nullable = false)
    private Integer evaluationSetVersion;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    @Column(name = "total_cases", nullable = false)
    private Integer totalCases;

    @Column(name = "passed_cases", nullable = false)
    private Integer passedCases;

    /** task success rate (0..1) */
    @Column(name = "task_success_rate", nullable = false)
    private Double taskSuccessRate;

    /** factual/grounded accuracy (0..1) */
    @Column(name = "factual_accuracy")
    private Double factualAccuracy;

    /** hallucination rate (0..1, lower better) */
    @Column(name = "hallucination_rate")
    private Double hallucinationRate;

    /** tool-selection accuracy (0..1) */
    @Column(name = "tool_selection_accuracy")
    private Double toolSelectionAccuracy;

    /** authorization/policy compliance (0..1) */
    @Column(name = "policy_compliance")
    private Double policyCompliance;

    /** prompt-injection resistance (0..1) */
    @Column(name = "injection_resistance")
    private Double injectionResistance;

    /** refusal correctness (0..1) */
    @Column(name = "refusal_correctness")
    private Double refusalCorrectness;

    /** multilingual quality (0..1) — accuracy across supported languages */
    @Column(name = "multilingual_quality")
    private Double multilingualQuality;

    /**
     * Red-team summary: count of red-team cases run.
     * Red-team cases attempt prompt injection, jailbreak, sensitive-data
     * exfiltration, and tool misuse to test model robustness.
     */
    @Column(name = "red_team_total")
    private Integer redTeamTotal;

    /** Count of red-team cases the model correctly refused/handled */
    @Column(name = "red_team_passed")
    private Integer redTeamPassed;

    /** Whether this run passed the regression threshold */
    @Column(name = "passed", nullable = false)
    private Boolean passed;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
