package com.omobio.platform.common.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Eligibility Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose eligibility checks required by the product-service and BFFs:
 *
 * <ul>
 *   <li>Check if a customer is eligible for a specific product / offer</li>
 *   <li>Get all eligible products / offers for a customer</li>
 *   <li>Check eligibility for plan migration / upgrade / downgrade</li>
 *   <li>Check eligibility for device financing / installment plans</li>
 * </ul>
 *
 * Note: EligibilityProvider is distinct from pricing. Eligibility says whether
 * a customer CAN get a product; pricing says how much it costs.
 * {@link com.omobio.product.adapter.ProductCatalogProvider} returns the catalog.
 *
 * All methods take {@code tenantId} as the first argument.
 */
public interface EligibilityProvider extends ApiAdapter {

    /**
     * Check if a customer is eligible for a specific product or offer.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param connectionId the connection ID (MSISDN)
     * @param productCode the operator-specific product code
     * @param offerCode optional offer/promotion code to check
     * @return eligibility result
     */
    EligibilityResult checkEligibility(String tenantId, String connectionId,
                                      String productCode, String offerCode);

    /**
     * Get all products and offers the customer is eligible for.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @return list of eligible products
     */
    List<EligibleProduct> getEligibleProducts(String tenantId, String connectionId);

    /**
     * Check eligibility for a plan migration (upgrade, downgrade, or change).
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param targetPlanCode the plan code to migrate to
     * @return migration eligibility result
     */
    MigrationEligibility checkMigrationEligibility(String tenantId, String connectionId,
                                                  String targetPlanCode);

    /**
     * Check eligibility for device financing / installment.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param deviceId the device identifier
     * @return financing eligibility result
     */
    FinancingEligibility checkFinancingEligibility(String tenantId, String connectionId,
                                                  String deviceId);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Result of an eligibility check.
     */
    record EligibilityResult(
            boolean eligible,
            String connectionId,
            String productCode,
            String offerCode,
            String eligibilityStatus, // ELIGIBLE, INELIGIBLE, PENDING, NOT_FOUND
            String reason,
            List<String> blockers,   // codes of blocking conditions
            Map<String, String> metadata
    ) {}

    /**
     * A product the customer is eligible for.
     */
    record EligibleProduct(
            String productCode,
            String productName,
            String productCategory,  // VOICE, DATA, SMS, BUNDLE, VAS, DEVICE
            boolean hasOffer,
            String offerCode,
            Instant offerExpiry,
            Map<String, String> eligibilityMetadata
    ) {}

    /**
     * Eligibility for plan migration.
     */
    record MigrationEligibility(
            String connectionId,
            String currentPlanCode,
            String targetPlanCode,
            boolean eligible,
            String reason,
            BigDecimal migrationFee,
            BigDecimal newPlanPrice,
            Instant effectiveDate,
            List<String> migrationConditions
    ) {}

    /**
     * Eligibility for device financing.
     */
    record FinancingEligibility(
            String connectionId,
            String deviceId,
            boolean eligible,
            String reason,
            BigDecimal monthlyInstallment,
            int installments,
            BigDecimal downPayment,
            String financingPartner
    ) {}
}
