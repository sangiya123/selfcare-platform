package com.selfcare.platform.common.adapter;

/**
 * Provider interface for retrieving and computing prices/pricing rules
 * from operator systems. Cross-industry (telco, insurance, etc.).
 *
 * Implementations live in industry packs: telco/dialog, insurance/aia, ...
 *
 * @see ADR-019: Provider Orchestration
 */
public interface PricingProvider extends ApiAdapter {

    /**
     * Get the active price for a product as of the given date.
     */
    PriceResult getActivePrice(String tenantId, String productId, java.time.Instant asOf);

    /**
     * Get the price for a product in a given currency.
     */
    PriceResult getPriceInCurrency(String tenantId, String productId, String currency, java.time.Instant asOf);

    /**
     * Get the price matrix (segmented by customer type, region, channel, ...).
     * Returns a list of price rules that apply to the same product.
     */
    java.util.List<PriceRule> getPriceMatrix(String tenantId, String productId);

    /**
     * Get the tax breakdown for a given price and customer.
     */
    TaxBreakdown getTaxBreakdown(String tenantId, String productId, String customerId, String amount, String currency);

    // --- Result types ---

    record PriceResult(
        String productId,
        String amount,
        String currency,
        java.time.Instant validFrom,
        java.time.Instant validTo,
        PriceType priceType
    ) {}

    record PriceRule(
        String productId,
        String segmentId,
        String region,
        String channel,
        String amount,
        String currency,
        java.time.Instant validFrom,
        java.time.Instant validTo
    ) {}

    record TaxBreakdown(
        String baseAmount,
        String taxAmount,
        String totalAmount,
        String currency,
        java.util.List<TaxLine> taxLines
    ) {}

    record TaxLine(
        String taxType,
        String rate,
        String amount
    ) {}

    enum PriceType {
        ONE_TIME, RECURRING, USAGE_BASED, TIERED
    }
}
