package com.selfcare.platform.common.adapter;

/**
 * Canonical payment result returned by operator {@link PaymentProviderAdapter}s.
 *
 * <p>The payment-service maps this back to its own {@code PaymentResult}
 * response type before surfacing it to callers.</p>
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PaymentResult {
    private String status;
    private String providerReference;
    private String receiptUrl;
    private String failureReason;
}