package com.selfcare.platform.common.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Recharge Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose the canonical recharge (top-up) capabilities required by the
 * payment-service and BFFs. RechargeProvider is a sub-capability of
 * {@code PaymentProviderAdapter} but is exposed as a separate interface
 * so it can be invoked directly (e.g. voucher PIN redemption, card top-up).
 *
 * Supports:
 * <ul>
 *   <li>Voucher (scratch card PIN) recharge</li>
 *   <li>Card / payment-instrument top-up</li>
 *   <li>Recharge history per connection</li>
 *   <li>Recharge status query</li>
 * </ul>
 *
 * All methods take {@code tenantId} as the first argument.
 */
public interface RechargeProvider extends ApiAdapter {

    /**
     * Redeem a voucher (scratch card) PIN to top up a connection.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param connectionId the connection ID (MSISDN)
     * @param voucherPin the 14-16 digit PIN printed on the scratch card
     * @return recharge result
     */
    RechargeResult rechargeByVoucher(String tenantId, String connectionId, String voucherPin);

    /**
     * Top up a connection using a card / payment token.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param amount the top-up amount
     * @param paymentToken the card / payment-instrument token from the payment gateway
     * @return recharge result
     */
    RechargeResult rechargeByCard(String tenantId, String connectionId,
                                  BigDecimal amount, String paymentToken);

    /**
     * Get the recharge history for a connection.
     *
     * @param tenantId the tenant identifier
     * @param connectionId the connection ID
     * @param limit max number of records to return (newest first)
     * @return list of recharge records
     */
    List<RechargeRecord> getRechargeHistory(String tenantId, String connectionId, int limit);

    /**
     * Query the status of a previously submitted recharge transaction.
     *
     * @param tenantId the tenant identifier
     * @param transactionId the operator's transaction reference
     * @return current transaction status
     */
    RechargeStatus queryRechargeStatus(String tenantId, String transactionId);

    /**
     * Validate a voucher PIN without redeeming it (e.g. to check if the
     * PIN format is valid and the card has not been used).
     *
     * @param tenantId the tenant identifier
     * @param voucherPin the PIN to validate
     * @return validation result with face value if valid
     */
    VoucherValidation validateVoucher(String tenantId, String voucherPin);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Result of a recharge operation.
     */
    record RechargeResult(
            boolean success,
            String transactionId,
            String connectionId,
            BigDecimal amount,
            BigDecimal newBalance,
            String currency,
            RechargeStatusCode status,
            String failureReason
    ) {}

    enum RechargeStatusCode {
        SUCCESS, PENDING, FAILED, TIMEOUT, INVALID_VOUCHER, EXPIRED_VOUCHER, INSUFFICIENT_FUNDS
    }

    /**
     * A historical recharge record.
     */
    record RechargeRecord(
            String transactionId,
            String connectionId,
            Instant timestamp,
            BigDecimal amount,
            String currency,
            String rechargeMethod,   // VOUCHER, CARD, WALLET, BANK_TRANSFER
            String voucherPin,      // masked, e.g. "****1234"
            BigDecimal newBalance,
            RechargeStatusCode status
    ) {}

    /**
     * Current status of a previously submitted recharge.
     */
    record RechargeStatus(
            String transactionId,
            RechargeStatusCode status,
            BigDecimal amount,
            String failureReason
    ) {}

    /**
     * Voucher validation result.
     */
    record VoucherValidation(
            boolean valid,
            String voucherPin,
            BigDecimal faceValue,
            String currency,
            Instant expiryDate,
            String failureReason
    ) {}
}
