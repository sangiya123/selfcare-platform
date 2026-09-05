package com.omobio.airtel.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.LoyaltyProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Airtel Loyalty Provider — manages loyalty points and rewards for Airtel subscribers.
 *
 * <p>Implements the canonical {@link LoyaltyProvider} contract from platform-common.
 * Airtel uses API-key authentication for the loyalty BSS endpoints.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = LoyaltyProvider.class)
@RequiredArgsConstructor
public class AirtelLoyaltyProvider implements ApiAdapter, LoyaltyProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_BSS";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // LoyaltyProvider implementation
    // ================================================================

    @Override
    public PointsBalance getPointsBalance(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/loyalty/" + connectionId + "/balance";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            return new PointsBalance(
                    connectionId,
                    toLong(m.get("currentPoints")),
                    toLong(m.get("pendingPoints")),
                    toLong(m.get("lifetimePoints")),
                    toLong(m.get("expiringPoints")),
                    parseInstant(m.get("nextExpiryDate")),
                    str(m.get("currency"), "LKR")
            );
        } catch (AirtelApiException e) {
            log.error("Airtel getPointsBalance failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public TierInfo getTier(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/v1/osp/loyalty/" + connectionId + "/tier";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            Object benefitsObj = m.get("tierBenefits");
            List<String> benefits = benefitsObj instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            return new TierInfo(
                    connectionId,
                    str(m.get("currentTier"), "BRONZE"),
                    toInteger(m.get("tierLevel")) != null ? toInteger(m.get("tierLevel")) : 1,
                    toLong(m.get("pointsToNextTier")),
                    toLong(m.get("currentTierPoints")),
                    toLong(m.get("nextTierThreshold")),
                    parseInstant(m.get("tierExpiryDate")),
                    benefits
            );
        } catch (AirtelApiException e) {
            log.error("Airtel getTier failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Reward> getAvailableRewards(String tenantId, String connectionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/v1/osp/loyalty/" + connectionId + "/rewards";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object rewardsObj = d.get("rewards");
            if (!(rewardsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<Reward>map(m -> new Reward(
                            str(m.get("rewardCode"), null),
                            str(m.get("rewardName"), null),
                            str(m.get("category"), null),
                            toLong(m.get("pointsCost")),
                            str(m.get("description"), null),
                            Boolean.TRUE.equals(m.get("available")),
                            parseInstant(m.get("expiryDate")),
                            toInteger(m.get("maxPerCustomer"))
                    ))
                    .toList();
        } catch (AirtelApiException e) {
            log.error("Airtel getAvailableRewards failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public RedemptionResult redeemReward(String tenantId, String connectionId, String rewardCode) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return new RedemptionResult(false, null, rewardCode, null, null, null, null, "No Airtel BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("rewardCode", rewardCode);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/loyalty/" + connectionId + "/redeem", body, null);
            if (resp == null) {
                return new RedemptionResult(false, null, rewardCode, null, null, null, null, "Empty BSS response");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new RedemptionResult(false, null, rewardCode, null, null, null, null, "Unexpected response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) d;
            boolean success = Boolean.TRUE.equals(m.get("success"));
            return new RedemptionResult(
                    success,
                    str(m.get("redemptionId"), null),
                    rewardCode,
                    toLong(m.get("pointsRedeemed")),
                    str(m.get("voucherCode"), null),
                    str(m.get("redemptionReference"), null),
                    parseInstant(m.get("expiryDate")),
                    success ? null : str(m.get("failureReason"), "Redemption failed")
            );
        } catch (AirtelApiException e) {
            log.error("Airtel redeemReward failed for tenant={} connection={} reward={}: {}",
                    tenantId, connectionId, rewardCode, e.getMessage());
            return new RedemptionResult(false, null, rewardCode, null, null, null, null, e.getMessage());
        }
    }

    @Override
    public List<PointsTransaction> getPointsHistory(String tenantId, String connectionId, int limit) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of("limit", limit);
        String path = "/v1/osp/loyalty/" + connectionId + "/transactions";
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object txObj = d.get("transactions");
            if (!(txObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<PointsTransaction>map(m -> new PointsTransaction(
                            str(m.get("transactionId"), null),
                            connectionId,
                            parseInstant(m.get("timestamp")),
                            toLong(m.get("pointsDelta")),
                            toLong(m.get("balanceAfter")),
                            str(m.get("transactionType"), null),
                            str(m.get("source"), null),
                            str(m.get("description"), null)
                    ))
                    .toList();
        } catch (AirtelApiException e) {
            log.error("Airtel getPointsHistory failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }
}
