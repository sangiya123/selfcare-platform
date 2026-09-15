package com.selfcare.ai.service;

/**
 * Pluggable prediction scoring model interface.
 *
 * The platform ships with two implementations:
 * <ul>
 *   <li>{@link HeuristicScoringModel} — rule-based scoring (default, v1)</li>
 *   <li>{@link MlScoringModel} — calls a production ML inference endpoint</li>
 * </ul>
 *
 * Both implementations share the same output contract:
 * {@code ScoredPrediction(score, level, explanation, action)}.
 * This lets callers be completely agnostic to the model implementation.
 *
 * <p>To add a new model (e.g. XGBoost, scikit-learn, a hosted model service):
 * implement this interface, annotate with {@code @Component} with a unique
 * {@code @Qualifier}, and update the model-selection configuration.
 *
 * @see HeuristicScoringModel
 * @see MlScoringModel
 */
public interface PredictionScoringModel {

    /**
     * Score a subject for a given prediction type.
     *
     * @param predictionType one of: CHURN_RISK, DATA_EXHAUSTION, BILL_SHOCK,
     *                       PAYMENT_FAILURE, SERVICE_ISSUE, OFFER_CONVERSION,
     *                       COMPLAINT_ESCALATION, FRAUD_RISK, CLAIM_DELAY
     * @param features       the feature bundle for this subject
     * @return scored prediction with a normalized score [0, 1], risk level,
     *         human-readable explanation, and recommended action
     */
    PredictiveMLService.ScoredPrediction score(String predictionType,
                                               PredictiveMLService.Features features);

    /**
     * Human-readable name of this model, used in logs and the model-version field
     * of stored predictions.
     */
    String modelName();

    /**
     * Model version string, e.g. "heuristic-v1", "xgboost-prod-v2".
     */
    String modelVersion();
}
