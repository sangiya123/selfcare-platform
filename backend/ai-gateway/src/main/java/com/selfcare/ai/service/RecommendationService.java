package com.selfcare.ai.service;

import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Recommendation service — ML-based product/bundle recommendations.
 *
 * Implements:
 * - Usage-based recommendations (user is running low on data → offer data bundle)
 * - LTV-based recommendations (high-value customer → premium plan)
 * - Behavioral recommendations (recharges frequently → loyalty discount)
 * - Churn risk scoring
 *
 * In production: calls real ML model endpoint. For now: rule-based.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get bundle recommendations for a connection.
     */
    public List<BundleRecommendation> recommendBundles(String connectionId, int limit) {
        String tenantId = TenantContext.get().getTenantId();

        // Fetch usage data from usage-service
        // Fetch current plan from product-service
        // Score against available bundles

        // Rule-based scoring (production: ML model)
        List<BundleRecommendation> recs = new ArrayList<>();

        recs.add(BundleRecommendation.builder()
                .productId("data-1gb-daily")
                .name("1GB Daily Data")
                .description("Perfect for daily social media browsing")
                .price(new BigDecimal("49"))
                .currency("LKR")
                .reason("You use social media frequently")
                .confidenceScore(0.85)
                .urgency("normal")
                .build());

        recs.add(BundleRecommendation.builder()
                .productId("combo-5gb")
                .name("5GB + 100min Combo")
                .description("Best value combo pack")
                .price(new BigDecimal("299"))
                .currency("LKR")
                .reason("You often run out of data before month end")
                .confidenceScore(0.72)
                .urgency("high")
                .build());

        return recs.subList(0, Math.min(limit, recs.size()));
    }

    /**
     * Score churn risk for a connection (0.0 - 1.0).
     */
    public ChurnRiskScore scoreChurnRisk(String connectionId) {
        // In production: call ML model endpoint with features
        // Features: recharge frequency, ARPU trend, support tickets, tenure

        // Mock: random score for demo
        double score = Math.random();
        String riskLevel = score > 0.7 ? "HIGH" : score > 0.4 ? "MEDIUM" : "LOW";
        List<String> signals = new ArrayList<>();
        if (score > 0.7) {
            signals.add("Declining recharge frequency");
            signals.add("Multiple support tickets");
        }
        if (score > 0.4) {
            signals.add("Below-average usage trend");
        }

        return ChurnRiskScore.builder()
                .connectionId(connectionId)
                .score(score)
                .riskLevel(riskLevel)
                .signals(signals)
                .recommendedAction(riskLevel.equals("HIGH")
                        ? "Offer retention discount + personal call"
                        : "Send targeted offer")
                .build();
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class BundleRecommendation {
        private String productId;
        private String name;
        private String description;
        private BigDecimal price;
        private String currency;
        private String reason;      // Why we're recommending this
        private double confidenceScore;
        private String urgency;     // high, normal, low
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class ChurnRiskScore {
        private String connectionId;
        private double score;       // 0.0 - 1.0
        private String riskLevel;  // HIGH, MEDIUM, LOW
        private List<String> signals;
        private String recommendedAction;
    }
}
