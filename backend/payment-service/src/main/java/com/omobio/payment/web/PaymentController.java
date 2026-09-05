package com.omobio.payment.web;

import com.omobio.payment.domain.PaymentTransaction;
import com.omobio.payment.service.PaymentService;
import com.omobio.platform.common.dto.PaginationRequest;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
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

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.of(
                Map.of("status", "UP", "service", "payment-service"),
                TenantContext.get().getCorrelationId()));
    }
}