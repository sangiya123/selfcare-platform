package com.selfcare.ai.gateway.service;

import com.selfcare.ai.domain.PredictionResult;
import com.selfcare.ai.repository.PredictionResultRepository;
import com.selfcare.ai.service.HeuristicScoringModel;
import com.selfcare.ai.service.PredictiveMLService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictiveMLServiceTest {

    @Mock private PredictionResultRepository repository;
    private PredictiveMLService service;

    @BeforeEach
    void setUp() {
        service = new PredictiveMLService(repository, new HeuristicScoringModel());
        ReflectionTestUtils.setField(service, "modelVersion", "test-v1");
        ReflectionTestUtils.setField(service, "ttlHours", 24);
    }

    @Test
    @DisplayName("CHURN_RISK scores high for inactive customer with complaints")
    void churnRisk_highForInactive() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .daysSinceLastRecharge(45)
                .daysSinceLastLogin(30)
                .complaints90d(3)
                .hasActiveSubscription(false)
                .build();

        var r = service.precompute("t1", "CONNECTION", "conn-1", "CHURN_RISK", f);

        assertThat(r.getScore()).isGreaterThan(0.5);
        assertThat(r.getLevel()).isIn("HIGH", "CRITICAL");
        assertThat(r.getRecommendedAction()).isEqualTo("REACH_OUT");
        assertThat(r.getExplanation()).contains("recharge", "Inactive", "complaints");
    }

    @Test
    @DisplayName("CHURN_RISK scores low for active engaged customer")
    void churnRisk_lowForActive() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .daysSinceLastRecharge(2)
                .daysSinceLastLogin(1)
                .hasActiveSubscription(true)
                .build();

        var r = service.precompute("t1", "CONNECTION", "conn-1", "CHURN_RISK", f);

        assertThat(r.getScore()).isLessThan(0.5);
        assertThat(r.getLevel()).isIn("LOW", "MEDIUM");
    }

    @Test
    @DisplayName("DATA_EXHAUSTION scores high when allowance is low and reset is far")
    void dataExhaustion_highWhenLow() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .dataAllowanceRemainingPct(0.05)  // 5% left
                .daysUntilAllowanceReset(24)       // 24 days to reset
                .build();

        var r = service.precompute("t1", "CONNECTION", "conn-1", "DATA_EXHAUSTION", f);

        assertThat(r.getScore()).isGreaterThan(0.5);
        assertThat(r.getRecommendedAction()).isEqualTo("SHOW_OFFER");
    }

    @Test
    @DisplayName("BILL_SHOCK detects > 50% bill increase")
    void billShock_detectsSpike() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .lastBillAmount(2000.0)
                .priorBillAmount(1000.0)
                .build();

        var r = service.precompute("t1", "CONNECTION", "conn-1", "BILL_SHOCK", f);

        assertThat(r.getScore()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("PAYMENT_FAILURE scores high with many failures")
    void paymentFailure_high() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .successfulPayments90d(5)
                .failedPayments90d(8)
                .build();

        var r = service.precompute("t1", "CONNECTION", "conn-1", "PAYMENT_FAILURE", f);

        assertThat(r.getScore()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("CLAIM_DELAY scores high for new policy with prior claims")
    void claimDelay_newPolicy() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .policyAgeDays(15)
                .priorClaimsCount(5)
                .accountStatus("DUNNING")
                .build();

        var r = service.precompute("t1", "POLICY", "pol-1", "CLAIM_DELAY", f);

        assertThat(r.getScore()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("score is bounded to [0, 1]")
    void scoreBounded() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var f = PredictiveMLService.Features.builder()
                .daysSinceLastRecharge(365)
                .daysSinceLastLogin(365)
                .complaints90d(100)
                .loginTrendPct(-100)
                .hasActiveSubscription(false)
                .build();

        var r = service.precompute("t1", "CONNECTION", "conn-1", "CHURN_RISK", f);

        assertThat(r.getScore()).isLessThanOrEqualTo(1.0);
        assertThat(r.getScore()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("getLatest returns repository result")
    void getLatest_returnsRepoResult() {
        PredictionResult r = PredictionResult.builder()
                .predictionId("p-1")
                .tenantId("t1")
                .targetType("CONNECTION")
                .targetId("conn-1")
                .predictionType("CHURN_RISK")
                .score(0.7)
                .level("HIGH")
                .computedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(86400))
                .build();
        when(repository.findFirstByTenantIdAndTargetTypeAndTargetIdAndPredictionTypeOrderByComputedAtDesc(
                "t1", "CONNECTION", "conn-1", "CHURN_RISK"))
                .thenReturn(Optional.of(r));

        Optional<PredictionResult> result = service.getLatest("t1", "CONNECTION", "conn-1", "CHURN_RISK");

        assertThat(result).isPresent();
        assertThat(result.get().getLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("pruneExpired delegates to repository")
    void pruneExpired() {
        when(repository.deleteExpiredBefore(any())).thenReturn(7);
        assertThat(service.pruneExpired()).isEqualTo(7);
    }
}
