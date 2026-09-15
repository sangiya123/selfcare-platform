package com.selfcare.hutch.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.PaymentProviderAdapter;
import com.selfcare.platform.common.adapter.PaymentRequest;
import com.selfcare.platform.common.adapter.PaymentResult;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hutch Payment Provider — integrates with Hutch BSS payment APIs.
 *
 * <p>Implements the optional {@link PaymentProviderAdapter} contract so the
 * {@code PaymentProviderRouter} can dispatch payments for Hutch tenants.
 * Hutch BSS uses API-key authentication with a slightly different response
 * envelope (responses are wrapped in {@code { data: ... }}).</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link HutchHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = PaymentProviderAdapter.class)
@RequiredArgsConstructor
public class HutchPaymentProvider implements ApiAdapter, PaymentProviderAdapter {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // PaymentProviderAdapter
    // ================================================================

    @Override
    public PaymentResult execute(PaymentRequest tx, String receiptBaseUrl) {
        log.info("Hutch execute payment: txId={}, type={}, amount={} {}",
                tx.getTransactionId(), tx.getTransactionType(), tx.getAmount(), tx.getCurrency());

        if ("RECHARGE".equalsIgnoreCase(tx.getTransactionType())) {
            var r = recharge(tx.getTenantId(),
                    nullToEmpty(tx.getTargetConnectionId()),
                    tx.getAmount(),
                    tx.getPaymentToken());
            return PaymentResult.builder()
                    .status(r.isSuccess() ? "SUCCESS" : "FAILED")
                    .providerReference(r.getProviderReference())
                    .receiptUrl(r.isSuccess() ? receiptBaseUrl + "/receipts/" + tx.getTransactionId() : null)
                    .failureReason(r.getFailureReason())
                    .build();
        }
        if ("BILL_PAYMENT".equalsIgnoreCase(tx.getTransactionType())) {
            var r = payBill(tx.getTenantId(),
                    nullToEmpty(tx.getBillId()),
                    tx.getAmount(),
                    tx.getPaymentToken());
            return PaymentResult.builder()
                    .status(r.isSuccess() ? "SUCCESS" : "FAILED")
                    .providerReference(r.getProviderReference())
                    .receiptUrl(r.isSuccess() ? receiptBaseUrl + "/receipts/" + tx.getTransactionId() : null)
                    .failureReason(r.getFailureReason())
                    .build();
        }

        return PaymentResult.builder()
                .status("FAILED")
                .failureReason("Unsupported transaction type: " + tx.getTransactionType())
                .build();
    }

    @Override
    public PaymentResult queryStatus(PaymentRequest tx) {
        var status = queryStatus(tx.getTenantId(), nullToEmpty(tx.getProviderReference()));
        return PaymentResult.builder()
                .status(status.getStatus())
                .providerReference(tx.getProviderReference())
                .failureReason(status.getFailureReason())
                .build();
    }

    // ================================================================
    // Domain Methods
    // ================================================================

    public RechargeResult recharge(String targetMsisdn, BigDecimal amount, String paymentToken) {
        return recharge(TenantContextBridge.currentTenantId(), targetMsisdn, amount, paymentToken);
    }

    public RechargeResult recharge(String tenantId, String targetMsisdn, BigDecimal amount, String paymentToken) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return RechargeResult.builder().success(false).status("FAILED")
                    .failureReason("No Hutch BSS integration configured for tenant").build();
        }

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", targetMsisdn);
        body.put("amount", amount);
        body.put("currency", "LKR");
        body.put("idempotencyKey", idempotencyKey);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg, "/api/v1/payments/recharge", body, null);
            if (resp == null) {
                return RechargeResult.builder().success(false).status("FAILED")
                        .failureReason("Empty response from Hutch BSS").build();
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return RechargeResult.builder().success(false).status("FAILED")
                        .failureReason("Unexpected Hutch BSS response shape").build();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("status"), "UNKNOWN");
            return RechargeResult.builder()
                    .success("SUCCESS".equals(status))
                    .providerReference((String) data.get("transactionRef"))
                    .status(status)
                    .newBalance(toBigDecimal(data.get("newBalance")))
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch recharge failed for {}: {}", targetMsisdn, e.getMessage());
            return RechargeResult.builder()
                    .success(false).status("UNKNOWN")
                    .failureReason("Hutch BSS error: " + e.getMessage())
                    .build();
        }
    }

    public BillPaymentResult payBill(String billId, BigDecimal amount, String paymentToken) {
        return payBill(TenantContextBridge.currentTenantId(), billId, amount, paymentToken);
    }

    public BillPaymentResult payBill(String tenantId, String billId, BigDecimal amount, String paymentToken) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return BillPaymentResult.builder().success(false).status("FAILED")
                    .failureReason("No Hutch BSS integration configured for tenant").build();
        }

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("billId", billId);
        body.put("amount", amount);
        body.put("currency", "LKR");
        body.put("idempotencyKey", idempotencyKey);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg, "/api/v1/payments/bill", body, null);
            if (resp == null) {
                return BillPaymentResult.builder().success(false).status("FAILED")
                        .failureReason("Empty response from Hutch BSS").build();
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return BillPaymentResult.builder().success(false).status("FAILED")
                        .failureReason("Unexpected Hutch BSS response shape").build();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("status"), "UNKNOWN");
            return BillPaymentResult.builder()
                    .success("SUCCESS".equals(status))
                    .providerReference((String) data.get("paymentRef"))
                    .status(status)
                    .amountPaid(amount)
                    .remainingBalance(toBigDecimal(data.get("remainingBalance")))
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch bill payment failed for bill {}: {}", billId, e.getMessage());
            return BillPaymentResult.builder()
                    .success(false).status("UNKNOWN")
                    .failureReason("Hutch BSS error: " + e.getMessage())
                    .build();
        }
    }

    public TransactionStatus queryStatus(String providerReference) {
        return queryStatus(TenantContextBridge.currentTenantId(), providerReference);
    }

    public TransactionStatus queryStatus(String tenantId, String providerReference) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return TransactionStatus.builder().status("UNKNOWN")
                    .failureReason("No Hutch BSS integration configured for tenant").build();
        }
        try {
            String path = "/api/v1/payments/status/" + providerReference;
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) {
                return TransactionStatus.builder().status("UNKNOWN").providerReference(providerReference).build();
            }
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return TransactionStatus.builder()
                        .status(str(data.get("status"), "UNKNOWN"))
                        .providerReference(providerReference)
                        .build();
            }
            return TransactionStatus.builder().status("UNKNOWN").providerReference(providerReference).build();
        } catch (HutchApiException e) {
            return TransactionStatus.builder().status("UNKNOWN")
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
