package com.selfcare.platform.common.adapter;

/**
 * Contract operator payment providers implement so the payment-service
 * {@code PaymentProviderRouter} can dispatch transactions per tenant.
 *
 * <p>Canonical home of the payment-provider contract so telco industry packs
 * (Dialog, Hutch, Airtel) can implement it without depending on the
 * payment-service module.</p>
 */
public interface PaymentProviderAdapter extends ApiAdapter {

    /**
     * Execute a payment (recharge / bill payment / package purchase / transfer).
     *
     * @param request        canonical payment details
     * @param receiptBaseUrl base URL used to build the receipt link on success
     */
    PaymentResult execute(PaymentRequest request, String receiptBaseUrl);

    /**
     * Query payment status (for reconciliation of UNKNOWN transactions).
     *
     * @param request canonical payment details (providerReference identifies the tx)
     */
    PaymentResult queryStatus(PaymentRequest request);
}