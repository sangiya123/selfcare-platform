package com.omobio.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Production ML scoring model — calls a hosted ML inference endpoint.
 *
 * <p>Use this instead of {@link HeuristicScoringModel} when a trained model
 * is available. To activate, set:
 * <pre>
 * ai.prediction.model-provider=ml
 * ai.prediction.ml-endpoint-url=https://ml-service.internal/v1/predict
 * ai.prediction.ml-endpoint-key=...  (optional: API key for the ML service)
 * </pre>
 *
 * <p>The inference endpoint must accept:
 * <pre>
 * POST /v1/predict
 * Content-Type: application/json
 * Authorization: Bearer &lt;ai.prediction.ml-endpoint-key&gt;
 *
 * {
 *   "prediction_type": "CHURN_RISK",
 *   "features": {
 *     "days_since_last_recharge": 45,
 *     "days_since_last_login": 30,
 *     "support_tickets_30d": 2,
 *     ...
 *   }
 * }
 * </pre>
 *
 * <p>And return:
 * <pre>
 * {
 *   "score": 0.87,
 *   "level": "HIGH",
 *   "explanation": "Churn probability elevated due to inactivity and complaints",
 *   "recommended_action": "REACH_OUT"
 * }
 * </pre>
 *
 * <p>If the ML endpoint is unreachable, the model falls back to the
 * {@link HeuristicScoringModel} and logs a warning. Set
 * {@code ai.prediction.ml-fallback=true} to control this behavior.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.prediction.model-provider", havingValue = "ml")
public class MlScoringModel implements PredictionScoringModel {

    private final RestClient restClient;
    private final String mlEndpointUrl;
    private final String mlApiKey;
    private final boolean fallbackEnabled;
    private final HeuristicScoringModel heuristicFallback;

    public MlScoringModel(
            RestClient.Builder restClientBuilder,
            @Value("${ai.prediction.ml-endpoint-url:}") String mlEndpointUrl,
            @Value("${ai.prediction.ml-endpoint-key:}") String mlApiKey,
            @Value("${ai.prediction.ml-fallback:true}") boolean fallbackEnabled,
            HeuristicScoringModel heuristicFallback) {
        this.restClient = restClientBuilder
                .baseUrl(mlEndpointUrl != null && !mlEndpointUrl.isBlank()
                        ? mlEndpointUrl : "http://localhost:9999")
                .build();
        this.mlEndpointUrl = mlEndpointUrl;
        this.mlApiKey = mlApiKey;
        this.fallbackEnabled = fallbackEnabled;
        this.heuristicFallback = heuristicFallback;
    }

    @Override
    public PredictiveMLService.ScoredPrediction score(String predictionType,
                                                    PredictiveMLService.Features f) {
        if (mlEndpointUrl == null || mlEndpointUrl.isBlank()) {
            log.warn("ML endpoint URL not configured — using heuristic fallback");
            return heuristicFallback.score(predictionType, f);
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/v1/predict")
                    .header("Authorization", "Bearer " + mlApiKey)
                    .header("X-Prediction-Type", predictionType)
                    .body(Map.of(
                            "prediction_type", predictionType,
                            "features", featuresToMap(f)
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("ML endpoint returned null for type={} — heuristic fallback", predictionType);
                return heuristicFallback.score(predictionType, f);
            }
            double score = toDouble(response.get("score"));
            String level = String.valueOf(response.getOrDefault("level", "LOW"));
            String explanation = String.valueOf(response.getOrDefault("explanation", ""));
            String action = String.valueOf(response.getOrDefault("recommended_action", "NOTHING"));

            return new PredictiveMLService.ScoredPrediction(score, level, explanation, action);
        } catch (Exception e) {
            log.warn("ML inference failed for type={}: {} — heuristic fallback", predictionType, e.getMessage());
            if (fallbackEnabled) {
                return heuristicFallback.score(predictionType, f);
            }
            return new PredictiveMLService.ScoredPrediction(0.0, "LOW",
                    "ML inference failed: " + e.getMessage(), "NOTHING");
        }
    }

    @Override
    public String modelName() {
        return "ml-inference";
    }

    @Override
    public String modelVersion() {
        return "ml-v1";  // Override via ai.prediction.ml-model-version property if needed
    }

    private static Map<String, Object> featuresToMap(PredictiveMLService.Features f) {
        return Map.ofEntries(
                entry("days_since_last_recharge", f.getDaysSinceLastRecharge()),
                entry("days_since_last_login", f.getDaysSinceLastLogin()),
                entry("support_tickets_30d", f.getSupportTickets30d()),
                entry("complaints_90d", f.getComplaints90d()),
                entry("avg_monthly_spend", f.getAvgMonthlySpend()),
                entry("last_bill_amount", f.getLastBillAmount()),
                entry("prior_bill_amount", f.getPriorBillAmount()),
                entry("data_allowance_remaining_pct", f.getDataAllowanceRemainingPct()),
                entry("days_until_allowance_reset", f.getDaysUntilAllowanceReset()),
                entry("failed_payments_90d", f.getFailedPayments90d()),
                entry("successful_payments_90d", f.getSuccessfulPayments90d()),
                entry("offers_shown_30d", f.getOffersShown30d()),
                entry("offers_accepted_30d", f.getOffersAccepted30d()),
                entry("service_issues_count", f.getServiceIssuesCount()),
                entry("has_active_subscription", f.getHasActiveSubscription()),
                entry("login_trend_pct", f.getLoginTrendPct()),
                entry("is_new_customer", f.getIsNewCustomer()),
                entry("account_status", f.getAccountStatus()),
                entry("policy_age_days", f.getPolicyAgeDays()),
                entry("prior_claims_count", f.getPriorClaimsCount())
        );
    }

    private static Map.Entry<String, Object> entry(String k, Object v) {
        return Map.entry(k, v != null ? v : Map.of());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
