package com.omobio.payment.service;

import com.omobio.payment.domain.PaymentTransaction;
import com.omobio.payment.repository.PaymentTransactionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.ConflictException;
import com.omobio.platform.common.web.NotFoundException;
import com.omobio.platform.common.web.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment service — implements the full payment flow with idempotency,
 * state machine, and cross-connection authorization.
 *
 * Flow:
 *   1. Receive payment request with idempotency key
 *   2. Check idempotency — if existing transaction, return it
 *   3. Validate (actor/target, amount, step-up requirement)
 *   4. Create PENDING transaction
 *   5. Call payment provider
 *   6. Update status (SUCCESS / FAILED / UNKNOWN)
 *   7. Publish event
 *
 * ADR-006: Cross-connection payment authorization
 *   1. Session valid
 *   2. Actor connection linked to account
 *   3. Target linked to account under current relationship version
 *   4. Target supports action/LOB/product state
 *   5. Amount/currency/payment method within policy
 *   6. Step-up auth if policy requires
 *   7. Idempotency key valid and not already completed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final CrossConnectionAuthorizer authorizer;
    private final PaymentProviderRouter providerRouter;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_PAYMENT_EVENTS = "payment.events";

    /**
     * Process a payment with idempotency.
     * Returns existing transaction if idempotency key matches.
     */
    @Transactional
    public PaymentTransaction processPayment(PaymentRequest request) {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        log.info("Payment request: type={}, amount={} {}, target={}, idempotency={}",
                request.getTransactionType(), request.getAmount(), request.getCurrency(),
                request.getTargetConnectionId(), request.getIdempotencyKey());

        // 1. Idempotency check — return existing if same key
        if (request.getIdempotencyKey() != null) {
            Optional<PaymentTransaction> existing = transactionRepository
                    .findByTenantIdAndIdempotencyKey(tenantId, request.getIdempotencyKey());

            if (existing.isPresent()) {
                PaymentTransaction tx = existing.get();
                log.info("Returning existing transaction for idempotency key: txId={}, status={}",
                        tx.getTransactionId(), tx.getStatus());
                return tx;
            }
        }

        // 2. Authorize cross-connection payment (if applicable)
        boolean stepUpRequired = false;
        if (request.getTargetConnectionId() != null
                && !request.getTargetConnectionId().equals(request.getSourceConnectionId())) {
            authorizer.authorize(userId, request.getTargetConnectionId(), "PAY_BILL");
            stepUpRequired = authorizer.requiresStepUp(request.getAmount());
        }

        if (stepUpRequired && !Boolean.TRUE.equals(request.getStepUpCompleted())) {
            throw new BadRequestException("Step-up authentication required for this payment amount");
        }

        // 3. Create PENDING transaction
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .idempotencyKey(request.getIdempotencyKey())
                .transactionType(request.getTransactionType())
                .sourceConnectionId(request.getSourceConnectionId())
                .targetConnectionId(request.getTargetConnectionId())
                .billId(request.getBillId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .paymentToken(request.getPaymentToken())
                .provider(request.getProvider())
                .status("PENDING")
                .stepUpRequired(stepUpRequired)
                .stepUpCompleted(request.getStepUpCompleted())
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        transaction = transactionRepository.save(transaction);

        // 4. Call payment provider
        try {
            PaymentResult result = providerRouter.execute(transaction);

            // 5. Update status
            transaction.setStatus(result.getStatus());
            transaction.setProviderReference(result.getProviderReference());
            transaction.setReceiptUrl(result.getReceiptUrl());
            if (result.getStatus().equals("FAILED")) {
                transaction.setFailureReason(result.getFailureReason());
            }
            transaction.setCompletedAt(Instant.now());
            transaction.setUpdatedAt(Instant.now());

            transaction = transactionRepository.save(transaction);

            // 6. Publish event
            publishEvent(transaction);

            log.info("Payment completed: txId={}, status={}, provider={}",
                    transaction.getTransactionId(), result.getStatus(), result.getProviderReference());
            return transaction;
        } catch (Exception e) {
            log.error("Payment execution failed: txId={}, error={}", transaction.getTransactionId(), e.getMessage());
            transaction.setStatus("FAILED");
            transaction.setFailureReason("Provider error: " + e.getMessage());
            transaction.setCompletedAt(Instant.now());
            transaction = transactionRepository.save(transaction);
            publishEvent(transaction);
            throw new ServiceUnavailableException("PaymentProvider", e.getMessage(), true);
        }
    }

    /**
     * Get transaction by ID.
     */
    @Transactional(readOnly = true)
    public PaymentTransaction getTransaction(String transactionId) {
        String tenantId = TenantContext.get().getTenantId();
        return transactionRepository.findByTenantIdAndTransactionId(tenantId, transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction", transactionId));
    }

    /**
     * Get transaction by idempotency key.
     */
    @Transactional(readOnly = true)
    public PaymentTransaction getByIdempotencyKey(String idempotencyKey) {
        String tenantId = TenantContext.get().getTenantId();
        return transactionRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElseThrow(() -> new NotFoundException("Transaction", "key=" + idempotencyKey));
    }

    /**
     * Reconcile a transaction (called periodically for UNKNOWN transactions).
     */
    @Transactional
    public PaymentTransaction reconcile(String transactionId) {
        PaymentTransaction tx = getTransaction(transactionId);
        if (!"UNKNOWN".equals(tx.getStatus())) {
            throw new ConflictException("Transaction not in UNKNOWN state: " + tx.getStatus());
        }
        // Re-check with provider
        PaymentResult result = providerRouter.queryStatus(tx);
        tx.setStatus(result.getStatus());
        tx.setProviderReference(result.getProviderReference());
        tx.setCompletedAt(Instant.now());
        tx = transactionRepository.save(tx);
        publishEvent(tx);
        return tx;
    }

    private void publishEvent(PaymentTransaction transaction) {
        try {
            kafkaTemplate.send(TOPIC_PAYMENT_EVENTS, transaction.getTransactionId(), transaction);
        } catch (Exception e) {
            log.warn("Failed to publish payment event: {}", e.getMessage());
        }
    }

    // ============================================================
    // Inner types
    // ============================================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentRequest {
        private String transactionType;
        private String sourceConnectionId;
        private String targetConnectionId;
        private String billId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String paymentToken;
        private String provider;
        private String idempotencyKey;
        private Boolean stepUpCompleted;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentResult {
        private String status;
        private String providerReference;
        private String receiptUrl;
        private String failureReason;
    }
}