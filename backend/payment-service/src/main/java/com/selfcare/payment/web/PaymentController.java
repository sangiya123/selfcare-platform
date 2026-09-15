package com.selfcare.payment.web;

import com.selfcare.payment.domain.PaymentMethod;
import com.selfcare.payment.domain.PaymentTransaction;
import com.selfcare.payment.service.PaymentService;
import com.selfcare.payment.service.SavedPaymentMethodService;
import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Payment REST API.
 *
 * Endpoints:
 *   POST /api/v1/payments                  — Process payment
 *   GET  /api/v1/payments/{txId}           — Get transaction
 *   GET  /api/v1/payments/by-key/{key}     — Get by idempotency key
 *   POST /api/v1/payments/{txId}/reconcile — Reconcile UNKNOWN transaction
 *   GET  /api/v1/payments/me               — User's transaction history
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payments, recharge, bill payment, transaction history")
public class PaymentController {

    private final PaymentService paymentService;
    private final SavedPaymentMethodService savedPaymentMethodService;

    @PostMapping
    @Operation(summary = "Process payment", description = "Initiates a payment with idempotency")
    public ResponseEntity<ApiResponse<PaymentTransaction>> processPayment(
            @RequestBody PaymentService.PaymentRequest request) {
        PaymentTransaction tx = paymentService.processPayment(request);
        HttpStatus status = "SUCCESS".equals(tx.getStatus()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .body(ApiResponse.of(tx, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<ApiResponse<PaymentTransaction>> getTransaction(@PathVariable String transactionId) {
        PaymentTransaction tx = paymentService.getTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.of(tx, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/by-key/{idempotencyKey}")
    @Operation(summary = "Get transaction by idempotency key")
    public ResponseEntity<ApiResponse<PaymentTransaction>> getByIdempotencyKey(@PathVariable String idempotencyKey) {
        PaymentTransaction tx = paymentService.getByIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(ApiResponse.of(tx, TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/{transactionId}/reconcile")
    @Operation(summary = "Reconcile UNKNOWN transaction", description = "Re-checks provider status for UNKNOWN txns")
    public ResponseEntity<ApiResponse<PaymentTransaction>> reconcile(@PathVariable String transactionId) {
        PaymentTransaction tx = paymentService.reconcile(transactionId);
        return ResponseEntity.ok(ApiResponse.of(tx, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/history")
    @Operation(summary = "User's transaction history", description = "Paginated list of the authenticated user's payments, newest first")
    public ResponseEntity<ApiResponse<com.selfcare.platform.common.dto.PaginationResponse<PaymentTransaction>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Page<PaymentTransaction> result = paymentService.listHistory(page, size);
        com.selfcare.platform.common.dto.PaginationResponse<PaymentTransaction> body =
                com.selfcare.platform.common.dto.PaginationResponse.<PaymentTransaction>builder()
                        .items(result.getContent())
                        .page(result.getNumber())
                        .size(result.getSize())
                        .totalItems(result.getTotalElements())
                        .totalPages(result.getTotalPages())
                        .hasNext(result.hasNext())
                        .hasPrevious(result.hasPrevious())
                        .build();
        return ResponseEntity.ok(ApiResponse.of(body, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/saved-cards")
    @Operation(summary = "User's saved payment methods", description = "Tokenized cards and other saved payment methods for the current user")
    public ResponseEntity<ApiResponse<java.util.List<PaymentMethod>>> getSavedCards() {
        return ResponseEntity.ok(ApiResponse.of(
                savedPaymentMethodService.listForUser(),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.of(
                Map.of("status", "UP", "service", "payment-service"),
                TenantContext.get().getCorrelationId()));
    }
}