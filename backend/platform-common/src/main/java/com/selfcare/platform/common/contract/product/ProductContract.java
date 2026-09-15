package com.selfcare.platform.common.contract.product;

import java.util.List;
import java.util.Map;

/**
 * Canonical product/offer catalog contract. Industry-neutral: an "offer" is a sellable artifact
 * (telco pack, insurance policy, travel bundle), a "product" is the parent catalog entity.
 */
public final class ProductContract {

    private ProductContract() {}

    public record ProductSummary(
            String productId,
            String name,
            String category,
            String industry,
            String externalRef,
            boolean active) {
    }

    public record ProductDetail(
            ProductSummary summary,
            String description,
            Map<String, String> locales,
            List<String> media,
            Map<String, Object> attributes) {
    }

    public record OfferSummary(
            String offerId,
            String productId,
            String name,
            String type,
            String price,
            boolean enrolled,
            boolean eligible) {
    }

    public record PriceMatrixItem(
            String offerId,
            String amount,
            String currency,
            String taxAmount,
            String billingCycle) {
    }

    public record EligibilityCheckRequest(
            String productId,
            String offerId,
            String connectionId,
            String userId) {
    }

    public record EligibilityCheckResponse(
            boolean eligible,
            List<String> reasons,
            List<PriceMatrixItem> priceMatrix) {
    }

    public interface ProductService {
        List<ProductSummary> listProducts(String tenantId, String category);

        ProductDetail getProduct(String tenantId, String productId);

        EligibilityCheckResponse checkEligibility(String tenantId, EligibilityCheckRequest request);
    }
}