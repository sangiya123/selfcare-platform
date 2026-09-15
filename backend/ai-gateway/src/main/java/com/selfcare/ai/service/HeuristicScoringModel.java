package com.selfcare.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic / rule-based prediction scoring model.
 *
 * <p>Default implementation for the v1.0 platform. Uses deterministic
 * rules per prediction type (no ML model dependency). Provides predictable
 * behavior for testing, evaluation, and operator acceptance.</p>
 *
 * <p>When a trained model is available, set
 * {@code ai.prediction.model-provider=ml} and inject {@link MlScoringModel}
 * instead.</p>
 */
@Slf4j
@Component
@Primary
public class HeuristicScoringModel implements PredictionScoringModel {

    @Override
    public PredictiveMLService.ScoredPrediction score(String predictionType,
                                                      PredictiveMLService.Features f) {
        return switch (predictionType) {
            case "CHURN_RISK" -> scoreChurn(f);
            case "DATA_EXHAUSTION" -> scoreDataExhaustion(f);
            case "BILL_SHOCK" -> scoreBillShock(f);
            case "PAYMENT_FAILURE" -> scorePaymentFailure(f);
            case "SERVICE_ISSUE" -> scoreServiceIssue(f);
            case "OFFER_CONVERSION" -> scoreOfferConversion(f);
            case "COMPLAINT_ESCALATION" -> scoreComplaintEscalation(f);
            case "FRAUD_RISK" -> scoreFraudRisk(f);
            case "CLAIM_DELAY" -> scoreClaimDelay(f);
            default -> new PredictiveMLService.ScoredPrediction(0.0, "LOW",
                    "Unknown prediction type", "NOTHING");
        };
    }

    @Override
    public String modelName() {
        return "heuristic";
    }

    @Override
    public String modelVersion() {
        return "heuristic-v1";
    }

    // ---- Heuristics (previously inline in PredictiveMLService) ----

    private PredictiveMLService.ScoredPrediction scoreChurn(PredictiveMLService.Features f) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        if (f.getDaysSinceLastRecharge() != null && f.getDaysSinceLastRecharge() > 30) {
            score += 0.3;
            reasons.add("No recharge in " + f.getDaysSinceLastRecharge() + " days");
        }
        if (f.getDaysSinceLastLogin() != null && f.getDaysSinceLastLogin() > 21) {
            score += 0.25;
            reasons.add("Inactive for " + f.getDaysSinceLastLogin() + " days");
        }
        if (f.getComplaints90d() != null && f.getComplaints90d() >= 2) {
            score += 0.2;
            reasons.add(f.getComplaints90d() + " complaints in 90 days");
        }
        if (f.getLoginTrendPct() != null && f.getLoginTrendPct() < -30) {
            score += 0.15;
            reasons.add("Login trend down " + f.getLoginTrendPct() + "%");
        }
        if (Boolean.FALSE.equals(f.getHasActiveSubscription())) {
            score += 0.1;
        }
        score = Math.min(1.0, score);
        return new PredictiveMLService.ScoredPrediction(score, level(score),
                String.join("; ", reasons),
                score >= 0.5 ? "REACH_OUT" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreDataExhaustion(PredictiveMLService.Features f) {
        if (f.getDataAllowanceRemainingPct() == null || f.getDaysUntilAllowanceReset() == null) {
            return new PredictiveMLService.ScoredPrediction(0.0, "LOW", "No data usage info", "NOTHING");
        }
        double pct = f.getDataAllowanceRemainingPct();
        int daysToReset = f.getDaysUntilAllowanceReset();
        double score = (1.0 - pct) * Math.min(1.0, daysToReset / 30.0);
        score = Math.min(1.0, score);
        String reason = String.format("Data at %.0f%%, %d days to reset", pct * 100, daysToReset);
        return new PredictiveMLService.ScoredPrediction(score, level(score), reason,
                score >= 0.7 ? "SHOW_OFFER" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreBillShock(PredictiveMLService.Features f) {
        if (f.getLastBillAmount() == null || f.getPriorBillAmount() == null
                || f.getPriorBillAmount() <= 0) {
            return new PredictiveMLService.ScoredPrediction(0.0, "LOW", "No prior bill to compare", "NOTHING");
        }
        double ratio = f.getLastBillAmount() / f.getPriorBillAmount();
        double score = ratio >= 2.0 ? 1.0
                : ratio >= 1.5 ? 0.8
                : ratio >= 1.3 ? 0.5
                : ratio >= 1.1 ? 0.25
                : 0.0;
        return new PredictiveMLService.ScoredPrediction(score, level(score),
                String.format("Bill up %.0f%% vs prior", (ratio - 1) * 100),
                score >= 0.5 ? "REACH_OUT" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scorePaymentFailure(PredictiveMLService.Features f) {
        if (f.getSuccessfulPayments90d() == null || f.getFailedPayments90d() == null) {
            return new PredictiveMLService.ScoredPrediction(0.0, "LOW", "No payment history", "NOTHING");
        }
        int total = f.getSuccessfulPayments90d() + f.getFailedPayments90d();
        if (total == 0) return new PredictiveMLService.ScoredPrediction(0.0, "LOW", "No payments in window", "NOTHING");
        double failureRate = (double) f.getFailedPayments90d() / total;
        double score = Math.min(1.0, failureRate + 0.05);
        return new PredictiveMLService.ScoredPrediction(score, level(score),
                f.getFailedPayments90d() + " failed out of " + total + " attempts",
                score >= 0.3 ? "REACH_OUT" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreServiceIssue(PredictiveMLService.Features f) {
        double score = 0.0;
        if (f.getServiceIssuesCount() != null) {
            score = Math.min(1.0, f.getServiceIssuesCount() / 5.0);
        }
        return new PredictiveMLService.ScoredPrediction(score, level(score),
                "Service issues: " + f.getServiceIssuesCount(),
                score >= 0.5 ? "REACH_OUT" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreOfferConversion(PredictiveMLService.Features f) {
        if (f.getOffersShown30d() == null || f.getOffersAccepted30d() == null) {
            return new PredictiveMLService.ScoredPrediction(0.5, "MEDIUM", "Insufficient data — default", "SHOW_OFFER");
        }
        if (f.getOffersShown30d() == 0) {
            return new PredictiveMLService.ScoredPrediction(0.5, "MEDIUM", "No prior offers", "SHOW_OFFER");
        }
        double rate = (double) f.getOffersAccepted30d() / f.getOffersShown30d();
        return new PredictiveMLService.ScoredPrediction(rate, level(rate),
                String.format("Conversion rate: %d/%d", f.getOffersAccepted30d(), f.getOffersShown30d()),
                rate >= 0.3 ? "SHOW_OFFER" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreComplaintEscalation(PredictiveMLService.Features f) {
        if (f.getComplaints90d() == null) {
            return new PredictiveMLService.ScoredPrediction(0.0, "LOW", "No complaints", "NOTHING");
        }
        double score = Math.min(1.0, f.getComplaints90d() / 4.0);
        return new PredictiveMLService.ScoredPrediction(score, level(score),
                f.getComplaints90d() + " complaints in 90 days",
                score >= 0.5 ? "REACH_OUT" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreFraudRisk(PredictiveMLService.Features f) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        if (f.getFailedPayments90d() != null && f.getFailedPayments90d() >= 5) {
            score += 0.4;
            reasons.add("Many failed payments");
        }
        if (f.getDaysSinceLastLogin() != null && f.getDaysSinceLastLogin() <= 1
                && Boolean.TRUE.equals(f.getHasActiveSubscription())) {
            score += 0.2;
            reasons.add("Sudden login after long absence");
        }
        if ("SUSPENDED".equalsIgnoreCase(f.getAccountStatus())) {
            score += 0.3;
            reasons.add("Account suspended");
        }
        return new PredictiveMLService.ScoredPrediction(Math.min(1.0, score), level(score),
                String.join("; ", reasons), score >= 0.7 ? "REACH_OUT" : "NOTHING");
    }

    private PredictiveMLService.ScoredPrediction scoreClaimDelay(PredictiveMLService.Features f) {
        double score = 0.0;
        if (f.getPolicyAgeDays() != null && f.getPolicyAgeDays() < 30) {
            score += 0.2;
        }
        if (f.getPriorClaimsCount() != null && f.getPriorClaimsCount() >= 3) {
            score += 0.3;
        }
        if ("DUNNING".equalsIgnoreCase(f.getAccountStatus())) {
            score += 0.4;
        }
        score = Math.min(1.0, score);
        return new PredictiveMLService.ScoredPrediction(score, level(score),
                "Policy age=" + f.getPolicyAgeDays() + "d, prior claims=" + f.getPriorClaimsCount(),
                score >= 0.6 ? "REACH_OUT" : "NOTHING");
    }

    private static String level(double score) {
        if (score >= 0.8) return "CRITICAL";
        if (score >= 0.6) return "HIGH";
        if (score >= 0.4) return "MEDIUM";
        return "LOW";
    }
}
