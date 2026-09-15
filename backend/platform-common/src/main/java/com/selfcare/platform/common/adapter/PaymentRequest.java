package com.selfcare.platform.common.adapter;

import java.math.BigDecimal;

/**
 * Canonical payment request passed to operator {@link PaymentProviderAdapter}s.
 *
 * <p>Contains exactly the fields the payment providers need to dispatch a
 * charge / bill payment through the operator BSS. The payment-service maps its
 * {@code PaymentTransaction} entity onto this canonical shape before calling
 * a provider, so packs never depend on the payment-service module.</p>
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PaymentRequest {
    private String transactionId;
    private String tenantId;
    private String userId;
    private String transactionType;          // RECHARGE, BILL_PAYMENT, PACKAGE_PURCHASE, TRANSFER
    private String sourceConnectionId;
    private String targetConnectionId;
    private String billId;
    private BigDecimal amount;
    private String currency;
    private String paymentToken;
    private String providerReference;
}