package com.omobio.dialog.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.LoyaltyProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog Loyalty Provider — manages loyalty points and rewards for Dialog subscribers.
 *
 * <p>Implements the canonical {@link LoyaltyProvider} contract from platform-common.
 * Exposes points balance, tier info, reward catalog, and redemption capabilities
 * for the loyalty-service and BFFs.</p>
 *
 * <p>Per-tenant config (loyalty API base URL, API key) is loaded from MongoDB
 * via {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog Loyalty.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = LoyaltyProvider.class)
@RequiredArgsConstructor
public class DialogLoyaltyProvider implements ApiAdapter, LoyaltyProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // LoyaltyProvider implementation
    // ================================================================

    @Override
    public PointsBalance getPointsBalance(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/loyalty/" + connectionId + "/balance";
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new PointsBalance(
                    connectionId,
                    toLong(data.get("currentPoints")),
                    toLong(data.get("pendingPoints")),
                    toLong(data.get("lifetimePoints")),
                    toLong(data.get("expiringPoints")),
                    parseInstant(data.get("nextExpiryDate")),
                    str(data.get("currency"), "LKR")
            );
        } catch (DialogApiException e) {
            log.error("Dialog getPointsBalance failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public TierInfo getTier(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = "/loyalty/" + connectionId + "/tier";
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            Object benefitsObj = data.get("tierBenefits");
            List<String> benefits = benefitsObj instanceof List<?> l
                    ? l.stream().map(Object::toString).toList() : List.of();
            return new TierInfo(
                    connectionId,
                    str(data.get("currentTier"), "BRONZE"),
                    toInteger(data.get("tierLevel")) != null ? toInteger(data.get("tierLevel")) : 1,
                    toLong(data.get("pointsToNextTier")),
                    toLong(data.get("currentTierPoints")),
                    toLong(data.get("nextTierThreshold")),
                    parseInstant(data.get("tierExpiryDate")),
                    benefits
            );
        } catch (DialogApiException e) {
            log.error("Dialog getTier failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Reward> getAvailableRewards(String tenantId, String connectionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = "/loyalty/" + connectionId + "/rewards";
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return List.of();
            Object rewardsObj = data.get("rewards");
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
        } catch (DialogApiException e) {
            log.error("Dialog getAvailableRewards failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public RedemptionResult redeemReward(String tenantId, String connectionId, String rewardCode) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new RedemptionResult(false, null, rewardCode, null, null, null, null, "No Dialog BSS integration");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("rewardCode", rewardCode);

        try {
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/loyalty/" + connectionId + "/redeem", body, null, cfg.apiKey());
            if (data == null) {
                return new RedemptionResult(false, null, rewardCode, null, null, null, null, "Empty BSS response");
            }
            boolean success = Boolean.TRUE.equals(data.get("success"));
            return new RedemptionResult(
                    success,
                    str(data.get("redemptionId"), null),
                    rewardCode,
                    toLong(data.get("pointsRedeemed")),
                    str(data.get("voucherCode"), null),
                    str(data.get("redemptionReference"), null),
                    parseInstant(data.get("expiryDate")),
                    success ? null : str(data.get("failureReason"), "Redemption failed")
            );
        } catch (DialogApiException e) {
            log.error("Dialog redeemReward failed for tenant={} connection={} reward={}: {}",
                    tenantId, connectionId, rewardCode, e.getMessage());
            return new RedemptionResult(false, null, rewardCode, null, null, null, null, e.getMessage());
        }
    }

    @Override
    public List<PointsTransaction> getPointsHistory(String tenantId, String connectionId, int limit) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of("limit", limit);
        String path = "/loyalty/" + connectionId + "/transactions";
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return List.of();
            Object txObj = data.get("transactions");
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
        } catch (DialogApiException e) {
            log.error("Dialog getPointsHistory failed for tenant={} connection={}: {}",
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
