package com.omobio.platform.common.adapter;

import java.time.Instant;
import java.util.List;

/**
 * Provider interface for retrieving promotions / discounts / campaign offers
 * from operator systems. Cross-industry.
 *
 * Implementations live in industry packs: telco/dialog, insurance/aia, ...
 *
 * @see ADR-019: Provider Orchestration
 */
public interface PromotionProvider extends ApiAdapter {

    /**
     * Get the active promotions for a given product.
     */
    List<Promotion> getActivePromotions(String tenantId, String productId, Instant asOf);

    /**
     * Get all promotions targeting a specific customer segment.
     */
    List<Promotion> getPromotionsForSegment(String tenantId, String segmentId, Instant asOf);

    /**
     * Validate a promotion code at checkout.
     */
    PromotionValidationResult validatePromotionCode(String tenantId, String code, String productId, String customerId);

    /**
     * Apply a promotion (compute discount) for a given order.
     */
    PromotionApplyResult applyPromotion(String tenantId, String code, List<OrderLine> orderLines, String customerId);

    // --- Result types ---

    record Promotion(
        String id,
        String name,
        String description,
        String productId,
        String segmentId,
        DiscountType discountType,
        String discountValue,
        String maxDiscount,
        String minPurchase,
        Instant validFrom,
        Instant validTo,
        int usageLimit,
        int usageCount,
        boolean requiresCode
    ) {}

    enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT, FREE_TRIAL, BUNDLE, LOYALTY_POINTS
    }

    record PromotionValidationResult(
        boolean valid,
        String reason,
        String promotionId,
        String discountAmount,
        String currency
    ) {}

    record PromotionApplyResult(
        boolean applied,
        String orderTotalBefore,
        String totalDiscount,
        String orderTotalAfter,
        String currency,
        List<AppliedPromotion> appliedPromotions
    ) {}

    record AppliedPromotion(
        String promotionId,
        String code,
        String discountAmount
    ) {}

    record OrderLine(
        String productId,
        String quantity,
        String unitPrice,
        String currency
    ) {}
}
