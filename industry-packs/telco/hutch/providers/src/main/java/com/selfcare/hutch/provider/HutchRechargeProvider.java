package com.selfcare.hutch.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RechargeProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hutch Recharge Provider — top-up via voucher PIN or card payment.
 *
 * <p>Implements the canonical {@link RechargeProvider} contract from platform-common.
 * Hutch uses API-key authentication for the BSS recharge endpoints.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) loaded from MongoDB via
 * {@link HutchHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = RechargeProvider.class)
@RequiredArgsConstructor
public class HutchRechargeProvider implements ApiAdapter, RechargeProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // RechargeProvider implementation
    // ================================================================

    @Override
    public RechargeResult rechargeByVoucher(String tenantId, String connectionId, String voucherPin) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new RechargeResult(false, null, connectionId, null, null, "LKR",
                    RechargeStatusCode.FAILED, "No Hutch BSS integration configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", connectionId);
        body.put("voucherPin", voucherPin);
        body.put("idempotencyKey", java.util.UUID.randomUUID().toString());

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg, "/api/v1/recharge/voucher", body, null);
            if (resp == null) {
                return new RechargeResult(false, null, connectionId, null, null, "LKR",
                        RechargeStatusCode.FAILED, "Empty response from Hutch BSS");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new RechargeResult(false, null, connectionId, null, null, "LKR",
                        RechargeStatusCode.FAILED, "Unexpected Hutch BSS response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("status"), "UNKNOWN");
            RechargeStatusCode mapped;
            try { mapped = RechargeStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = RechargeStatusCode.FAILED; }
            boolean success = "SUCCESS".equals(status);
            return new RechargeResult(
                    success,
                    str(data.get("transactionRef"), null),
                    connectionId,
                    toBd(data.get("amount")),
                    toBd(data.get("newBalance")),
                    str(data.get("currency"), "LKR"),
                    mapped,
                    success ? null : "Hutch BSS error: " + status
            );
        } catch (HutchApiException e) {
            log.error("Hutch rechargeByVoucher failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new RechargeResult(false, null, connectionId, null, null, "LKR",
                    RechargeStatusCode.FAILED, e.getMessage());
        }
    }

    @Override
    public RechargeResult rechargeByCard(String tenantId, String connectionId,
                                          BigDecimal amount, String paymentToken) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                    RechargeStatusCode.FAILED, "No Hutch BSS integration configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", connectionId);
        body.put("amount", amount);
        body.put("currency", "LKR");
        if (paymentToken != null) body.put("paymentToken", paymentToken);
        body.put("idempotencyKey", java.util.UUID.randomUUID().toString());

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg, "/api/v1/recharge/card", body, null);
            if (resp == null) {
                return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                        RechargeStatusCode.FAILED, "Empty response from Hutch BSS");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                        RechargeStatusCode.FAILED, "Unexpected Hutch BSS response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("status"), "UNKNOWN");
            RechargeStatusCode mapped;
            try { mapped = RechargeStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = RechargeStatusCode.FAILED; }
            boolean success = "SUCCESS".equals(status);
            return new RechargeResult(
                    success,
                    str(data.get("transactionRef"), null),
                    connectionId,
                    amount,
                    toBd(data.get("newBalance")),
                    str(data.get("currency"), "LKR"),
                    mapped,
                    success ? null : "Hutch BSS error: " + status
            );
        } catch (HutchApiException e) {
            log.error("Hutch rechargeByCard failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                    RechargeStatusCode.FAILED, e.getMessage());
        }
    }

    @Override
    public List<RechargeRecord> getRechargeHistory(String tenantId, String connectionId, int limit) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of("limit", limit);
        String path = "/api/v1/recharge/history/" + connectionId;
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, queryParams, null);
            if (resp == null) return List.of();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) return List.of();
            Object recordsObj = d.get("records");
            if (!(recordsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<RechargeRecord>map(m -> new RechargeRecord(
                            str(m.get("transactionRef"), null),
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
        } catch (HutchApiException e) {
            log.error("Hutch getRechargeHistory failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public RechargeStatus queryRechargeStatus(String tenantId, String transactionId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new RechargeStatus(transactionId, RechargeStatusCode.FAILED, null, "No Hutch BSS integration");
        }

        String path = "/api/v1/recharge/status/" + transactionId;
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) {
                return new RechargeStatus(transactionId, RechargeStatusCode.PENDING, null, null);
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new RechargeStatus(transactionId, RechargeStatusCode.PENDING, null, null);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            return new RechargeStatus(
                    transactionId,
                    parseRechargeStatus(str(data.get("status"), null)),
                    toBd(data.get("amount")),
                    str(data.get("failureReason"), null)
            );
        } catch (HutchApiException e) {
            log.error("Hutch queryRechargeStatus failed for tenant={} tx={}: {}",
                    tenantId, transactionId, e.getMessage());
            return new RechargeStatus(transactionId, RechargeStatusCode.FAILED, null, e.getMessage());
        }
    }

    @Override
    public VoucherValidation validateVoucher(String tenantId, String voucherPin) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Hutch BSS not configured for tenant={}", tenantId);
            return new VoucherValidation(false, voucherPin, null, "LKR", null, "No Hutch BSS integration");
        }

        var queryParams = Map.<String, Object>of("voucherPin", voucherPin);
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, "/api/v1/recharge/voucher/validate", queryParams, null);
            if (resp == null) {
                return new VoucherValidation(false, voucherPin, null, "LKR", null, "Empty response");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new VoucherValidation(false, voucherPin, null, "LKR", null, "Unexpected response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            return new VoucherValidation(
                    Boolean.TRUE.equals(data.get("valid")),
                    voucherPin,
                    toBd(data.get("faceValue")),
                    str(data.get("currency"), "LKR"),
                    parseInstant(data.get("expiryDate")),
                    str(data.get("failureReason"), null)
            );
        } catch (HutchApiException e) {
            log.error("Hutch validateVoucher failed for tenant={}: {}", tenantId, e.getMessage());
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
