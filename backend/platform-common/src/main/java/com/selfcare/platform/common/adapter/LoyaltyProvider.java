package com.selfcare.platform.common.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Loyalty Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose loyalty / rewards capabilities required by the loyalty-service and BFFs:
 *
 * <ul>
 *   <li>Get loyalty points balance for a connection</li>
 *   <li>Get the tier / membership level</li>
 *   <li>List available rewards the customer can redeem</li>
 *   <li>Redeem points for a reward</li>
 *   <li>Get loyalty points transaction history</li>
 * </ul>
 *
 * All methods take {@code tenantId} as the first argument.
 */
public interface LoyaltyProvider extends ApiAdapter {

    /**
     * Get the loyalty points balance and tier for a connection.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param connectionId the connection ID (MSISDN)
     * @return points balance
     */
    PointsBalance getPointsBalance(String tenantId, String connectionId);

    /**
     * Get the loyalty tier / membership level for a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return tier details
     */
    TierInfo getTier(String tenantId, String connectionId);

    /**
     * List all available rewards the customer can redeem with their points.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return list of available rewards
     */
    List<Reward> getAvailableRewards(String tenantId, String connectionId);

    /**
     * Redeem points for a specific reward.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param rewardCode the reward code to redeem
     * @return redemption result
     */
    RedemptionResult redeemReward(String tenantId, String connectionId, String rewardCode);

    /**
     * Get the loyalty points transaction history.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param limit max number of records to return (newest first)
     * @return list of transactions
     */
    List<PointsTransaction> getPointsHistory(String tenantId, String connectionId, int limit);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Loyalty points balance snapshot.
     */
    record PointsBalance(
            String connectionId,
            Long currentPoints,
            Long pendingPoints,
            Long lifetimePoints,
            Long expiringPoints,         // points that will expire in the next 90 days
            Instant nextExpiryDate,
            String currency
    ) {}

    /**
     * Loyalty tier / membership level details.
     */
    record TierInfo(
            String connectionId,
            String currentTier,           // BRONZE, SILVER, GOLD, PLATINUM
            int tierLevel,                // 1..4
            Long pointsToNextTier,
            Long currentTierPoints,
            Long nextTierThreshold,
            Instant tierExpiryDate,
            List<String> tierBenefits
    ) {}

    /**
     * A reward the customer can redeem.
     */
    record Reward(
            String rewardCode,
            String rewardName,
            String category,              // DATA, VOICE, SMS, DEVICE, MERCHANDISE, VOUCHER
            Long pointsCost,
            String description,
            boolean available,
            Instant expiryDate,
            Integer maxPerCustomer
    ) {}

    /**
     * Result of a reward redemption.
     */
    record RedemptionResult(
            boolean success,
            String redemptionId,
            String rewardCode,
            Long pointsRedeemed,
            String voucherCode,           // for voucher-type rewards
            String redemptionReference,   // operator's reference
            Instant expiryDate,
            String failureReason
    ) {}

    /**
     * A points transaction (earn / redeem / expire / adjustment).
     */
    record PointsTransaction(
            String transactionId,
            String connectionId,
            Instant timestamp,
            Long pointsDelta,             // positive=earned, negative=redeemed
            Long balanceAfter,
            String transactionType,       // EARN, REDEEM, EXPIRE, ADJUSTMENT, BONUS
            String source,                // RECHARGE, USAGE, PROMOTION, REFERRAL
            String description
    ) {}

    // import for reference — see also BigDecimal for cashback rewards
    // (kept as a separate field elsewhere; not all operators support cashback)
    record CashbackReward(
            String rewardCode,
            BigDecimal cashbackAmount,
            String currency,
            Long pointsCost
    ) {}
}
