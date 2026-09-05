package com.omobio.dialog.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RechargeProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.tenant.TenantContextBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dialog Recharge Provider — top-up via voucher PIN or card payment.
 *
 * <p>Implements the canonical {@link RechargeProvider} contract from platform-common.
 * Supports scratch-card (voucher) redemption and card-based top-up via the
 * Dialog MIFE payment gateway.</p>
 *
 * <p>Per-tenant config (MIFE base URL, client ID/secret) loaded from MongoDB
 * via {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog MIFE.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = RechargeProvider.class)
@RequiredArgsConstructor
public class DialogRechargeProvider implements ApiAdapter, RechargeProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_MIFE";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // RechargeProvider implementation
    // ================================================================

    @Override
    public RechargeResult rechargeByVoucher(String tenantId, String connectionId, String voucherPin) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog MIFE not configured for tenant={}", tenantId);
            return new RechargeResult(false, null, connectionId, null, null, "LKR",
                    RechargeStatusCode.FAILED, "No Dialog MIFE integration configured");
        }

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("voucherPin", voucherPin);
        body.put("idempotencyKey", idempotencyKey);
        if (cfg.clientId() != null) body.put("clientId", cfg.clientId());

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/recharge/voucher", body, token, cfg.apiKey());
            if (data == null) {
                return new RechargeResult(false, null, connectionId, null, null, "LKR",
                        RechargeStatusCode.FAILED, "Empty response from Dialog MIFE");
            }
            String status = str(data.get("status"), "UNKNOWN");
            RechargeStatusCode mapped;
            try { mapped = RechargeStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = RechargeStatusCode.FAILED; }
            boolean success = "SUCCESS".equals(status);
            return new RechargeResult(
                    success,
                    str(data.get("transactionId"), null),
                    connectionId,
                    toBd(data.get("amount")),
                    toBd(data.get("newBalance")),
                    str(data.get("currency"), "LKR"),
                    mapped,
                    success ? null : "MIFE error: " + status
            );
        } catch (DialogApiException e) {
            log.error("Dialog rechargeByVoucher failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new RechargeResult(false, null, connectionId, null, null, "LKR",
                    RechargeStatusCode.FAILED, e.getMessage());
        }
    }

    @Override
    public RechargeResult rechargeByCard(String tenantId, String connectionId,
                                          BigDecimal amount, String paymentToken) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog MIFE not configured for tenant={}", tenantId);
            return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                    RechargeStatusCode.FAILED, "No Dialog MIFE integration configured");
        }

        String idempotencyKey = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("amount", amount);
        body.put("currency", "LKR");
        body.put("paymentMethod", "CARD");
        body.put("paymentToken", paymentToken != null ? paymentToken : "");
        body.put("idempotencyKey", idempotencyKey);
        if (cfg.clientId() != null) body.put("clientId", cfg.clientId());

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/recharge/card", body, token, cfg.apiKey());
            if (data == null) {
                return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                        RechargeStatusCode.FAILED, "Empty response from Dialog MIFE");
            }
            String status = str(data.get("status"), "UNKNOWN");
            RechargeStatusCode mapped;
            try { mapped = RechargeStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = RechargeStatusCode.FAILED; }
            boolean success = "SUCCESS".equals(status);
            return new RechargeResult(
                    success,
                    str(data.get("transactionId"), null),
                    connectionId,
                    amount,
                    toBd(data.get("newBalance")),
                    str(data.get("currency"), "LKR"),
                    mapped,
                    success ? null : "MIFE error: " + status
            );
        } catch (DialogApiException e) {
            log.error("Dialog rechargeByCard failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                    RechargeStatusCode.FAILED, e.getMessage());
        }
    }

    @Override
    public List<RechargeRecord> getRechargeHistory(String tenantId, String connectionId, int limit) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog MIFE not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of("limit", limit);
        String path = "/recharge/history/" + connectionId;
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, queryParams, null, cfg.apiKey());
            if (data == null) return List.of();
            Object recordsObj = data.get("records");
            if (!(recordsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<RechargeRecord>map(m -> new RechargeRecord(
                            str(m.get("transactionId"), null),
                            connectionId,
                            parseInstant(m.get("timestamp")),
                            toBd(m.get("amount")),
                            str(m.get("currency"), "LKR"),
                            str(m.get("rechargeMethod"), null),
                            maskPin(str(m.get("voucherPin"), null)),
                            toBd(m.get("newBalance")),
                            parseRechargeStatus(str(m.get("status"), null))
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getRechargeHistory failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public RechargeStatus queryRechargeStatus(String tenantId, String transactionId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog MIFE not configured for tenant={}", tenantId);
            return new RechargeStatus(transactionId, RechargeStatusCode.FAILED, null, "No Dialog MIFE integration");
        }

        String path = "/recharge/status/" + transactionId;
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, token, cfg.apiKey());
            if (data == null) {
                return new RechargeStatus(transactionId, RechargeStatusCode.PENDING, null, null);
            }
            return new RechargeStatus(
                    transactionId,
                    parseRechargeStatus(str(data.get("status"), null)),
                    toBd(data.get("amount")),
                    str(data.get("failureReason"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog queryRechargeStatus failed for tenant={} tx={}: {}",
                    tenantId, transactionId, e.getMessage());
            return new RechargeStatus(transactionId, RechargeStatusCode.FAILED, null, e.getMessage());
        }
    }

    @Override
    public VoucherValidation validateVoucher(String tenantId, String voucherPin) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Dialog MIFE not configured for tenant={}", tenantId);
            return new VoucherValidation(false, voucherPin, null, "LKR", null, "No Dialog MIFE integration");
        }

        var queryParams = Map.<String, Object>of("voucherPin", voucherPin);
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                    "/recharge/voucher/validate", queryParams, null, cfg.apiKey());
            if (data == null) {
                return new VoucherValidation(false, voucherPin, null, "LKR", null, "Empty response");
            }
            return new VoucherValidation(
                    Boolean.TRUE.equals(data.get("valid")),
                    voucherPin,
                    toBd(data.get("faceValue")),
                    str(data.get("currency"), "LKR"),
                    parseInstant(data.get("expiryDate")),
                    str(data.get("failureReason"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog validateVoucher failed for tenant={}: {}", tenantId, e.getMessage());
            return new VoucherValidation(false, voucherPin, null, "LKR", null, e.getMessage());
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }

    private RechargeStatusCode parseRechargeStatus(String s) {
        if (s == null) return RechargeStatusCode.PENDING;
        try { return RechargeStatusCode.valueOf(s.toUpperCase()); }
        catch (Exception e) { return RechargeStatusCode.PENDING; }
    }

    private String maskPin(String pin) {
        if (pin == null || pin.length() < 4) return "****";
        return "****" + pin.substring(pin.length() - 4);
    }
}
