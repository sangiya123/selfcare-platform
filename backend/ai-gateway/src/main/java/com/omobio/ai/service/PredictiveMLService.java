package com.omobio.ai.service;

import com.omobio.ai.domain.PredictionResult;
import com.omobio.ai.repository.PredictionResultRepository;
import com.omobio.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Predictive ML service — precomputes risk/behavioral predictions per
 * the AI scope section 2 of OMOBIO_Global_Selfcare_ALL_MARKDOWN_DOCUMENTS.
 *
 * Per the spec:
 *  - Predictions should generally be asynchronous/precomputed
 *  - Served from a read model
 *  - Do not put expensive model inference in the critical dashboard path
 *
 * This service implements nine prediction types from the spec:
 *   1. CHURN_RISK          — likelihood of a customer leaving
 *   2. DATA_EXHAUSTION     — likelihood of running out before reset
 *   3. BILL_SHOCK          — likelihood of a bill > 1.5x prior bill
 *   4. PAYMENT_FAILURE     — likelihood of next payment failing
 *   5. SERVICE_ISSUE       — likelihood of a support ticket arising
 *   6. OFFER_CONVERSION    — likelihood of accepting an offer
 *   7. COMPLAINT_ESCALATION — likelihood of complaint escalating
 *   8. FRAUD_RISK          — likelihood of fraudulent activity
 *   9. CLAIM_DELAY         — insurance: likelihood of claim being delayed
 *
 * <p><b>Model swapping:</b> Set {@code ai.prediction.model-provider=heuristic}
 * (default) to use rule-based scoring, or {@code ai.prediction.model-provider=ml}
 * to call a production ML inference endpoint (requires
 * {@code ai.prediction.ml-endpoint-url} to be configured). Both paths share the
 * same output contract ({@link ScoredPrediction}), so callers are unaffected.
 */
@Slf4j
@Service
public class PredictiveMLService {

    private final PredictionResultRepository repository;
    private final PredictionScoringModel scoringModel;

    @Value("${ai.prediction.model-version:heuristic-v1}")
    private String modelVersion;

    @Value("${ai.prediction.ttl-hours:24}")
    private int ttlHours;

    private static final String TENANT_PLACEHOLDER = "SYSTEM_BATCH";

    /**
     * Constructor accepting the pluggable scoring model.
     * Spring injects either {@link HeuristicScoringModel} or {@link MlScoringModel}
     * based on the {@code ai.prediction.model-provider} property.
     */
    public PredictiveMLService(PredictionResultRepository repository,
                              PredictionScoringModel scoringModel) {
        this.repository = repository;
        this.scoringModel = scoringModel;
    }

    // ----- Per-prediction feature sources (would be Kafka / API in production) -----

    /**
     * Feature input bundle for a single subject.
     * The platform batch jobs collect these from MySQL/Mongo via Kafka-fed
     * read models and call {@link #precompute(String, String, String, Map)}
     * once per subject per type.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Features {
        private Integer daysSinceLastRecharge;
        private Integer daysSinceLastLogin;
        private Integer supportTickets30d;
        private Integer complaints90d;
        private Double avgMonthlySpend;
        private Double lastBillAmount;
        private Double priorBillAmount;
        private Double dataAllowanceRemainingPct;
        private Integer daysUntilAllowanceReset;
        private Integer failedPayments90d;
        private Integer successfulPayments90d;
        private Integer offersShown30d;
        private Integer offersAccepted30d;
        private Integer serviceIssuesCount;
        private Boolean hasActiveSubscription;
        private Integer loginTrendPct; // negative = decreasing
        private Boolean isNewCustomer;
        private String accountStatus; // ACTIVE, SUSPENDED, DUNNING
        private Integer policyAgeDays; // insurance
        private Integer priorClaimsCount; // insurance
    }

    // ----- Public API (read model) -----

    @Transactional(readOnly = true)
    public Optional<PredictionResult> getLatest(String tenantId, String targetType,
                                                String targetId, String predictionType) {
        return repository
                .findFirstByTenantIdAndTargetTypeAndTargetIdAndPredictionTypeOrderByComputedAtDesc(
                        tenantId, targetType, targetId, predictionType);
    }

    @Transactional(readOnly = true)
    public List<PredictionResult> getActiveForTarget(String tenantId, String targetType, String targetId) {
        return repository.findByTenantIdAndTargetTypeAndTargetIdAndExpiresAtAfterOrderByComputedAtDesc(
                tenantId, targetType, targetId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PredictionResult> getHighRiskConnections(String tenantId, String predictionType, int minLevel) {
        List<String> levels = new ArrayList<>();
        if (minLevel >= 4) levels.add("CRITICAL");
        if (minLevel >= 3) levels.add("HIGH");
        if (minLevel >= 2) levels.add("MEDIUM");
        if (levels.isEmpty()) levels.add("LOW");
        return repository.findActiveByTypeAndLevel(tenantId, predictionType, levels, Instant.now());
    }

    // ----- Precompute (called by the batch job) -----

    /**
     * Precompute a single prediction for a subject and write to the read model.
     * Tenant context is set to the batch tenant for traceability.
     */
    @Transactional
    public PredictionResult precompute(String tenantId, String targetType, String targetId,
                                      String predictionType, Features features) {
        // Run the model
        ScoredPrediction scored = score(predictionType, features);
        Instant now = Instant.now();

        PredictionResult result = PredictionResult.builder()
                .predictionId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .targetType(targetType)
                .targetId(targetId)
                .predictionType(predictionType)
                .score(scored.score)
                .level(scored.level)
                .features(toJson(features))
                .explanation(scored.explanation)
                .recommendedAction(scored.action)
                .modelVersion(modelVersion)
                .computedAt(now)
                .expiresAt(now.plus(ttlHours, ChronoUnit.HOURS))
                .build();

        PredictionResult saved = repository.save(result);
        log.debug("Prediction precomputed: tenant={}, type={}, target={}, score={}, level={}",
                tenantId, predictionType, targetId, saved.getScore(), saved.getLevel());
        return saved;
    }

    @Transactional
    public int pruneExpired() {
        return repository.deleteExpiredBefore(Instant.now());
    }

    // ----- Scoring (delegates to the configured model) -----

    /**
     * Score a subject using the configured model (heuristic or ML).
     * @see PredictionScoringModel
     */
    private ScoredPrediction score(String type, Features f) {
        return scoringModel.score(type, f);
    }

    private static String level(double score) {
        if (score >= 0.8) return "CRITICAL";
        if (score >= 0.6) return "HIGH";
        if (score >= 0.4) return "MEDIUM";
        return "LOW";
    }

    private static String toJson(Features f) {
        return "{" +
                "daysSinceLastRecharge=" + f.getDaysSinceLastRecharge() +
                ",daysSinceLastLogin=" + f.getDaysSinceLastLogin() +
                ",dataRemainingPct=" + f.getDataAllowanceRemainingPct() +
                ",failedPayments=" + f.getFailedPayments90d() +
                ",successfulPayments=" + f.getSuccessfulPayments90d() +
                "}";
    }

    /**
     * Scored prediction output — shared contract between heuristic and ML models.
     */
    public record ScoredPrediction(double score, String level, String explanation, String action) {}
}
