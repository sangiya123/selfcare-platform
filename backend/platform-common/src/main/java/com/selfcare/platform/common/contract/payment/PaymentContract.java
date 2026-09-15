package com.selfcare.platform.common.contract.payment;

import java.time.Instant;
import java.util.List;

/**
 * Canonical payment contract — initiate, track, reconcile. Idempotency is part of the contract.
 */
public final class PaymentContract {

    private PaymentContract() {}

    public record PaymentInitiateRequest(
            String tenantId,
            String userId,
            String connectionId,
            String offerId,
            String amount,
            String currency,
            String method,
            String idempotencyKey,
            String description,
            String returnUrl) {
    }

    public record PaymentInitiateResponse(
            String paymentId,
            PaymentState status,
            String redirectUrl,
            String idempotencyKey) {
    }

    public record PaymentStatus(
            String paymentId,
            PaymentState status,
            String amount,
            String currency,
            Instant paidAt,
            String receiptId) {
    }

    public record ReceiptResponse(
            String receiptId,
            String paymentId,
            String amount,
            String currency,
            String reference,
            Instant issuedAt) {
    }

    public record ReconcileReport(
            long completed,
            long failed,
            long pending,
            String totalAmount,
            String currency) {
    }

    public record PaymentHistoryItem(String paymentId, String amount, String currency, PaymentState status,
                                     Instant createdAt) {
    }

    public record PaymentHistory(List<PaymentHistoryItem> items) {
    }

    public enum PaymentState {
        PENDING, INITIATED, AUTHORIZED, COMPLETED, FAILED, REFUNDED, TIMEOUT
    }

    public interface PaymentService {
        PaymentInitiateResponse initiate(PaymentInitiateRequest request);

        PaymentStatus getStatus(String tenantId, String paymentId);

        ReconcileReport reconcile(String tenantId, String date);
    }
}