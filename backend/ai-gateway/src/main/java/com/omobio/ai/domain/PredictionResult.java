package com.omobio.ai.domain;

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
 * Predictive ML prediction result — stored as a read model per the AI scope spec.
 *
 * Per AI scope section 2: "Predictions should generally be asynchronous/precomputed
 * and served from a read model; do not put expensive model inference in the
 * critical dashboard path without evidence."
 *
 * The PredictiveMLService computes predictions on a schedule and writes
 * results here. The dashboard reads from this table — not from the LLM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_predictions", indexes = {
    @Index(name = "ix_pred_tenant", columnList = "tenant_id"),
    @Index(name = "ix_pred_tenant_target", columnList = "tenant_id, target_type, target_id"),
    @Index(name = "ix_pred_tenant_type_computed", columnList = "tenant_id, prediction_type, computed_at"),
    @Index(name = "ix_pred_expires", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
public class PredictionResult {

    @Id
    @Column(name = "prediction_id", length = 64)
    private String predictionId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    /**
     * Type of subject the prediction is about:
     *  - CONNECTION (a specific connection)
     *  - USER (a user account across connections)
     *  - POLICY (insurance policy)
     */
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 128)
    private String targetId;

    /**
     * Type of prediction:
     *  - CHURN_RISK
     *  - DATA_EXHAUSTION
     *  - BILL_SHOCK
     *  - PAYMENT_FAILURE
     *  - SERVICE_ISSUE
     *  - OFFER_CONVERSION
     *  - COMPLAINT_ESCALATION
     *  - FRAUD_RISK
     *  - CLAIM_DELAY
     */
    @Column(name = "prediction_type", nullable = false, length = 32)
    private String predictionType;

    /** Score in [0.0, 1.0] — higher = more likely */
    @Column(name = "score", nullable = false)
    private Double score;

    /** Categorical level: LOW, MEDIUM, HIGH, CRITICAL */
    @Column(name = "level", length = 16)
    private String level;

    /** JSON of contributing features / model input summary */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /** Human-readable explanation: "10 days since last recharge; data at 5% of allowance" */
    @Column(name = "explanation", length = 1024)
    private String explanation;

    /** Recommended action the dashboard can take: REACH_OUT, SHOW_OFFER, NOTHING */
    @Column(name = "recommended_action", length = 64)
    private String recommendedAction;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
