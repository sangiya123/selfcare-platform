package com.omobio.platform.common.adapter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Canonical interface for payment gateway calls.
 * Implementations vary per payment processor (Stripe, bKash, mPesa, etc.).
 *
 * <p>All calls are idempotent — use {@code idempotencyKey} to prevent duplicate
 * charges. Audit-logged with {@code action=PAYMENT_GATEWAY_CALL}.
 *
 * @see com.omobio.payment.service.PaymentService
 */
public interface PaymentProvider extends ApiAdapter {

    // ─── One-time payment ─────────────────────────────────────────────────

    /**
     * Initiate a one-time payment (card, mobile money, bank transfer).
     *
     * @param tenantId       the tenant
     * @param idempotencyKey  unique key per attempt — prevents duplicate charges
     * @param amount          amount in minor units (e.g. cents, paisa)
     * @param currency        ISO 4217 currency code
     * @param channel         payment channel: {@code card}, {@code bkash}, {@code mpesa}, {@code bank_transfer}
     * @param connectionId    connection / policy / booking being paid
     * @param returnUrl       redirect URL after payment
     * @param metadata        extra params per channel
     * @return                gateway response with {@code transactionId} and {@code redirectUrl}
     */
    PaymentInitResponse initiatePayment(
            String tenantId,
            String idempotencyKey,
            BigDecimal amount,
            String currency,
            String channel,
            String connectionId,
            String returnUrl,
            Map<String, String> metadata
    );

    /**
     * Capture / confirm a pre-authorised payment.
     *
     * @param tenantId      the tenant
     * @param transactionId gateway transaction ID from {@code initiatePayment}
     * @return              capture result
     */
    PaymentCaptureResponse capturePayment(
            String tenantId,
            String transactionId
    );

    // ─── Refund ─────────────────────────────────────────────────────────

    /**
     * Full or partial refund of a captured payment.
     *
     * @param tenantId      the tenant
     * @param transactionId  original transaction ID
     * @param amount        amount to refund (null = full refund)
     * @param reason        reason code
     * @param idempotencyKey idempotency key for the refund
     * @return              refund result with {@code refundId}
     */
    RefundResponse refundPayment(
            String tenantId,
            String transactionId,
            BigDecimal amount,
            String reason,
            String idempotencyKey
    );

    // ─── Status ────────────────────────────────────────────────────────

    /**
     * Query the status of a payment / refund.
     *
     * @param tenantId      the tenant
     * @param transactionId gateway transaction ID or refund ID
     * @return              current status
     */
    PaymentStatusResponse getPaymentStatus(
            String tenantId,
            String transactionId
    );

    // ─── Webhook ───────────────────────────────────────────────────────

    /**
     * Validate and parse a webhook payload from the payment processor.
     * Returns the canonical event regardless of processor-specific format.
     *
     * @param tenantId the tenant
     * @param rawBody  raw request body
     * @param headers  request headers (for signature verification)
     * @return         normalised payment event, or throws on invalid signature
     */
    PaymentEvent parseWebhook(
            String tenantId,
            String rawBody,
            Map<String, String> headers
    );

    // ─── Inner types ───────────────────────────────────────────────────

    record PaymentInitResponse(
            String transactionId,
            String status,           // PENDING | READY | FAILED
            String redirectUrl,       // null for card-present
            BigDecimal amount,
            String currency,
            Map<String, String> channelData   // channel-specific fields
    ) {}

    record PaymentCaptureResponse(
            String transactionId,
            String status,            // COMPLETED | FAILED | PENDING
            BigDecimal amount,
            BigDecimal fee,
            String gatewayReference
    ) {}

    record RefundResponse(
            String refundId,
            String transactionId,
            String status,           // COMPLETED | PENDING | FAILED
            BigDecimal amountRefunded,
            BigDecimal feeRefunded
    ) {}

    record PaymentStatusResponse(
            String transactionId,
            String status,            // PENDING | COMPLETED | FAILED | REFUNDED | PARTIALLY_REFUNDED
            BigDecimal amount,
            BigDecimal refundedAmount,
            String failureReason
    ) {}

    enum PaymentEventType {
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        REFUND_COMPLETED,
        REFUND_FAILED
    }

    record PaymentEvent(
            PaymentEventType type,
            String transactionId,
            String refundId,
            BigDecimal amount,
            String currency,
            String gatewayReference
    ) {}
}
