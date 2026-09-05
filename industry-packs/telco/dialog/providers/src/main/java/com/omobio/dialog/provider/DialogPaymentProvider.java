package com.omobio.dialog.provider;

import com.omobio.payment.domain.PaymentTransaction;
import com.omobio.payment.service.PaymentProviderRouter.PaymentProviderAdapter;
import com.omobio.payment.service.PaymentService;
import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dialog Payment Provider — integrates with Dialog MIFE payment APIs.
 *
 * <p>Implements the optional {@link PaymentProviderAdapter} contract from
 * the payment-service so the {@code PaymentProviderRouter} can dispatch
 * transactions to this provider based on tenant. Also exposes domain
 * methods (recharge / payBill) for direct invocation.</p>
 *
 * <p>Per-tenant config (base URL, client ID/secret) loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog MIFE.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = PaymentProviderAdapter.class)
@RequiredArgsConstructor
public class DialogPaymentProvider implements ApiAdapter, PaymentProviderAdapter {

    private static final String INTEGRATION_TYPE = "DIALOG_MIFE";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // PaymentProviderAdapter
    // ================================================================

    @Override
    public PaymentService.PaymentResult execute(PaymentTransaction tx, String receiptBaseUrl) {
        log.info("Dialog execute payment: txId={}, type={}, amount={} {}",
                tx.getTransactionId(), tx.getTransactionType(), tx.getAmount(), tx.getCurrency());

        if ("RECHARGE".equalsIgnoreCase(tx.getTransactionType())) {
            var r = recharge(tx.getTenantId(),
                    nullToEmpty(tx.getTargetConnectionId()),
                    tx.getAmount(),
                    tx.getPaymentToken());
            return PaymentService.PaymentResult.builder()
                    .status(r.success() ? "SUCCESS" : "FAILED")
                    .providerReference(r.providerReference())
                    .receiptUrl(r.success() ? receiptBaseUrl + "/receipts/" + tx.getTransactionId() : null)
                    .failureReason(r.failureReason())
                    .build();
        }
        if ("BILL_PAYMENT".equalsIgnoreCase(tx.getTransactionType())) {
            var r = payBill(tx.getTenantId(),
                    nullToEmpty(tx.getBillId()),
                    tx.getAmount(),
                    tx.getPaymentToken());
            return PaymentService.PaymentResult.builder()
                    .status(r.success() ? "SUCCESS" : "FAILED")
                    .providerReference(r.providerReference())
                    .receiptUrl(r.success() ? receiptBaseUrl + "/receipts/" + tx.getTransactionId() : null)
                    .failureReason(r.failureReason())
                    .build();
        }

        return PaymentService.PaymentResult.builder()
                .status("FAILED")
                .failureReason("Unsupported transaction type: " + tx.getTransactionType())
                .build();
    }

    @Override
    public PaymentService.PaymentResult queryStatus(PaymentTransaction tx) {
        TransactionStatus status = queryStatus(tx.getTenantId(), nullToEmpty(tx.getProviderReference()));
        return PaymentService.PaymentResult.builder()
                .status(status.status())
                .providerReference(status.providerReference())
                .failureReason(status.failureReason())
                .build();
    }

    // ================================================================
    // Domain Methods
    // ================================================================

    public RechargeResult recharge(String targetMsisdn, BigDecimal amount, String paymentToken) {
        return recharge(TenantContextBridge.currentTenantId(), targetMsisdn, amount, paymentToken);
    }

    public RechargeResult recharge(String tenantId, String targetMsisdn, BigDecimal amount, String paymentToken) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return RechargeResult.builder().success(false).status("FAILED")
                    .failureReason("No Dialog MIFE integration configured for tenant").build();
        }

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", targetMsisdn);
        body.put("amount", amount);
        body.put("currency", "LKR");
        body.put("paymentMethod", "WALLET");
        body.put("paymentToken", paymentToken != null ? paymentToken : "");
        body.put("clientId", cfg.clientId() != null ? cfg.clientId() : "");
        body.put("idempotencyKey", idempotencyKey);

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/payments/recharge", body, token, cfg.apiKey());
            if (data == null) {
                return RechargeResult.builder().success(false).status("FAILED")
                        .failureReason("Empty response from Dialog MIFE").build();
            }
            String status = str(data.get("status"), "UNKNOWN");
            return RechargeResult.builder()
                    .success("SUCCESS".equals(status))
                    .providerReference((String) data.get("transactionId"))
                    .status(status)
                    .newBalance(toBigDecimal(data.get("newBalance")))
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog recharge failed for {}: {}", targetMsisdn, e.getMessage());
            return RechargeResult.builder()
                    .success(false)
                    .status("UNKNOWN")
                    .failureReason("MIFE error: " + e.getMessage())
                    .build();
        }
    }

    public BillPaymentResult payBill(String billId, BigDecimal amount, String paymentToken) {
        return payBill(TenantContextBridge.currentTenantId(), billId, amount, paymentToken);
    }

    public BillPaymentResult payBill(String tenantId, String billId, BigDecimal amount, String paymentToken) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return BillPaymentResult.builder().success(false).status("FAILED")
                    .failureReason("No Dialog MIFE integration configured for tenant").build();
        }

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("billId", billId);
        body.put("amount", amount);
        body.put("currency", "LKR");
        body.put("paymentToken", paymentToken != null ? paymentToken : "");
        body.put("clientId", cfg.clientId() != null ? cfg.clientId() : "");
        body.put("idempotencyKey", idempotencyKey);

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/payments/bill", body, token, cfg.apiKey());
            if (data == null) {
                return BillPaymentResult.builder().success(false).status("FAILED")
                        .failureReason("Empty response from Dialog MIFE").build();
            }
            String status = str(data.get("status"), "UNKNOWN");
            return BillPaymentResult.builder()
                    .success("SUCCESS".equals(status))
                    .providerReference((String) data.get("paymentId"))
                    .status(status)
                    .amountPaid(amount)
                    .remainingBalance(toBigDecimal(data.get("remainingBalance")))
                    .build();
        } catch (DialogApiException e) {
            log.error("Dialog bill payment failed for bill {}: {}", billId, e.getMessage());
            return BillPaymentResult.builder()
                    .success(false)
                    .status("UNKNOWN")
                    .failureReason("MIFE error: " + e.getMessage())
                    .build();
        }
    }

    public TransactionStatus queryStatus(String providerReference) {
        return queryStatus(TenantContextBridge.currentTenantId(), providerReference);
    }

    public TransactionStatus queryStatus(String tenantId, String providerReference) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return TransactionStatus.builder().status("UNKNOWN")
                    .failureReason("No Dialog MIFE integration configured for tenant").build();
        }
        String path = "/payments/status/" + providerReference;
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, token, cfg.apiKey());
            if (data == null) {
                return TransactionStatus.builder().status("UNKNOWN").providerReference(providerReference).build();
            }
            return TransactionStatus.builder()
                    .status(str(data.get("status"), "UNKNOWN"))
                    .providerReference(providerReference)
                    .build();
        } catch (DialogApiException e) {
            return TransactionStatus.builder()
                    .status("UNKNOWN")
                    .providerReference(providerReference)
                    .failureReason(e.getMessage())
                    .build();
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    // ================================================================
    // Result DTOs
    // ================================================================

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class RechargeResult {
        private boolean success;
        private String providerReference;
        private String status;
        private BigDecimal newBalance;
        private String failureReason;
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class BillPaymentResult {
        private boolean success;
        private String providerReference;
        private String status;
        private BigDecimal amountPaid;
        private BigDecimal remainingBalance;
        private String failureReason;
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class TransactionStatus {
        private String status;
        private String providerReference;
        private String failureReason;
    }
}
