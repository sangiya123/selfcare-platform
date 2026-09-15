package com.selfcare.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HeuristicScoringModel}.
 *
 * Validates the v1 heuristic scoring rules. The {@link MlScoringModel}
 * uses these same scoring rules as a fallback, so any test that passes
 * here also validates the fallback path.
 */
class HeuristicScoringModelTest {

    private HeuristicScoringModel model;

    @BeforeEach
    void setUp() {
        model = new HeuristicScoringModel();
    }

    @Test
    @DisplayName("CHURN_RISK: long-inactive customer scores HIGH")
    void churnInactive() {
        PredictiveMLService.Features f = PredictiveMLService.Features.builder()
                .daysSinceLastRecharge(45)
                .daysSinceLastLogin(30)
                .complaints90d(2)
                .build();
        PredictiveMLService.ScoredPrediction s = model.score("CHURN_RISK", f);
        assertThat(s.score()).isGreaterThanOrEqualTo(0.6);
        assertThat(s.level()).isIn("HIGH", "CRITICAL");
        assertThat(s.action()).isEqualTo("REACH_OUT");
    }

    @Test
    @DisplayName("DATA_EXHAUSTION: low data + days to reset scores HIGH")
    void dataExhaustion() {
        PredictiveMLService.Features f = PredictiveMLService.Features.builder()
                .dataAllowanceRemainingPct(0.1)
                .daysUntilAllowanceReset(24)
                .build();
        PredictiveMLService.ScoredPrediction s = model.score("DATA_EXHAUSTION", f);
        assertThat(s.score()).isGreaterThan(0.5);
        assertThat(s.action()).isEqualTo("SHOW_OFFER");
    }

    @Test
    @DisplayName("BILL_SHOCK: 2x prior bill scores CRITICAL")
    void billShock() {
        PredictiveMLService.Features f = PredictiveMLService.Features.builder()
                .lastBillAmount(200.0)
                .priorBillAmount(100.0)
                .build();
        PredictiveMLService.ScoredPrediction s = model.score("BILL_SHOCK", f);
        assertThat(s.score()).isEqualTo(1.0);
        assertThat(s.level()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("PAYMENT_FAILURE: high failure rate scores HIGH")
    void paymentFailure() {
        PredictiveMLService.Features f = PredictiveMLService.Features.builder()
                .failedPayments90d(4)
                .successfulPayments90d(6)
                .build();
        PredictiveMLService.ScoredPrediction s = model.score("PAYMENT_FAILURE", f);
        assertThat(s.score()).isGreaterThanOrEqualTo(0.3);
        assertThat(s.action()).isEqualTo("REACH_OUT");
    }

    @Test
    @DisplayName("FRAUD_RISK: suspended account + many failed payments scores CRITICAL")
    void fraudRisk() {
        PredictiveMLService.Features f = PredictiveMLService.Features.builder()
                .failedPayments90d(6)
                .daysSinceLastLogin(1)
                .hasActiveSubscription(true)
                .accountStatus("SUSPENDED")
                .build();
        PredictiveMLService.ScoredPrediction s = model.score("FRAUD_RISK", f);
        assertThat(s.score()).isGreaterThanOrEqualTo(0.7);
        assertThat(s.level()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("CLAIM_DELAY: dunning + many prior claims scores HIGH")
    void claimDelay() {
        PredictiveMLService.Features f = PredictiveMLService.Features.builder()
                .accountStatus("DUNNING")
                .priorClaimsCount(4)
                .build();
        PredictiveMLService.ScoredPrediction s = model.score("CLAIM_DELAY", f);
        assertThat(s.score()).isGreaterThanOrEqualTo(0.6);
        assertThat(s.action()).isEqualTo("REACH_OUT");
    }

    @Test
    @DisplayName("Unknown prediction type returns LOW/0.0")
    void unknownType() {
        PredictiveMLService.ScoredPrediction s = model.score("UNKNOWN_TYPE",
                PredictiveMLService.Features.builder().build());
        assertThat(s.score()).isEqualTo(0.0);
        assertThat(s.level()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("modelName and modelVersion are stable")
    void identity() {
        assertThat(model.modelName()).isEqualTo("heuristic");
        assertThat(model.modelVersion()).isEqualTo("heuristic-v1");
    }
}
