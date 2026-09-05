package com.omobio.airtel.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RechargeProvider;
import com.omobio.platform.common.adapter.RegisterAdapter;
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
 * Airtel Recharge Provider — top-up via voucher PIN or card payment.
 *
 * <p>Implements the canonical {@link RechargeProvider} contract from platform-common.
 * Airtel Money uses an OAuth2 {@code client_credentials} flow; voucher PINs are
 * redeemed via the BSS. Response shape: {@code { data: { ... } }}.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = RechargeProvider.class)
@RequiredArgsConstructor
public class AirtelRechargeProvider implements ApiAdapter, RechargeProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_GATEWAY";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // RechargeProvider implementation
    // ================================================================

    @Override
    public RechargeResult rechargeByVoucher(String tenantId, String connectionId, String voucherPin) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel Gateway not configured for tenant={}", tenantId);
            return new RechargeResult(false, null, connectionId, null, null, "LKR",
                    RechargeStatusCode.FAILED, "No Airtel Gateway integration configured");
        }

        String reference = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("voucherPin", voucherPin);
        body.put("reference", reference);
        if (cfg.merchantId() != null) body.put("merchantId", cfg.merchantId());

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.post(tenantId, cfg, "/v1/osp/recharge/voucher", body, token);
            if (resp == null) {
                return new RechargeResult(false, null, connectionId, null, null, "LKR",
                        RechargeStatusCode.FAILED, "Empty response from Airtel");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new RechargeResult(false, null, connectionId, null, null, "LKR",
                        RechargeStatusCode.FAILED, "Unexpected Airtel response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("transactionStatus"), "UNKNOWN");
            RechargeStatusCode mapped;
            try { mapped = RechargeStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = RechargeStatusCode.FAILED; }
            boolean success = "SUCCESS".equals(status) || "TS".equals(status);
            return new RechargeResult(
                    success,
                    str(data.get("transactionId"), null),
                    connectionId,
                    toBd(data.get("amount")),
                    toBd(data.get("newBalance")),
                    str(data.get("currency"), "LKR"),
                    mapped,
                    success ? null : "Airtel error: " + status
            );
        } catch (AirtelApiException e) {
            log.error("Airtel rechargeByVoucher failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new RechargeResult(false, null, connectionId, null, null, "LKR",
                    RechargeStatusCode.FAILED, e.getMessage());
        }
    }

    @Override
    public RechargeResult rechargeByCard(String tenantId, String connectionId,
                                          BigDecimal amount, String paymentToken) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel Gateway not configured for tenant={}", tenantId);
            return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                    RechargeStatusCode.FAILED, "No Airtel Gateway integration configured");
        }

        String reference = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("subscriberId", connectionId);
        body.put("amount", amount);
        body.put("currency", "LKR");
        body.put("reference", reference);
        if (paymentToken != null) body.put("paymentToken", paymentToken);
        if (cfg.merchantId() != null) body.put("merchantId", cfg.merchantId());

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.post(tenantId, cfg, "/v1/osp/recharge/card", body, token);
            if (resp == null) {
                return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                        RechargeStatusCode.FAILED, "Empty response from Airtel");
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> d)) {
                return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                        RechargeStatusCode.FAILED, "Unexpected Airtel response shape");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d;
            String status = str(data.get("transactionStatus"), "UNKNOWN");
            RechargeStatusCode mapped;
            try { mapped = RechargeStatusCode.valueOf(status.toUpperCase()); }
            catch (Exception e) { mapped = RechargeStatusCode.FAILED; }
            boolean success = "SUCCESS".equals(status) || "TS".equals(status);
            return new RechargeResult(
                    success,
                    str(data.get("transactionId"), null),
                    connectionId,
                    amount,
                    toBd(data.get("newBalance")),
                    str(data.get("currency"), "LKR"),
                    mapped,
                    success ? null : "Airtel error: " + status
            );
        } catch (AirtelApiException e) {
            log.error("Airtel rechargeByCard failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return new RechargeResult(false, null, connectionId, amount, null, "LKR",
                    RechargeStatusCode.FAILED, e.getMessage());
        }
    }

    @Override
    public List<RechargeRecord> getRechargeHistory(String tenantId, String connectionId, int limit) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel Gateway not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of("limit", limit);
        String path = "/v1/osp/recharge/history/" + connectionId;
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
        } catch (AirtelApiException e) {
            log.error("Airtel getRechargeHistory failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public RechargeStatus queryRechargeStatus(String tenantId, String transactionId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel Gateway not configured for tenant={}", tenantId);
            return new RechargeStatus(transactionId, RechargeStatusCode.FAILED, null, "No Airtel Gateway integration");
        }

        String path = "/v1/osp/recharge/status/" + transactionId;
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, token);
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
                    parseRechargeStatus(str(data.get("transactionStatus"), null)),
                    toBd(data.get("amount")),
                    str(data.get("failureReason"), null)
            );
        } catch (AirtelApiException e) {
            log.error("Airtel queryRechargeStatus failed for tenant={} tx={}: {}",
                    tenantId, transactionId, e.getMessage());
            return new RechargeStatus(transactionId, RechargeStatusCode.FAILED, null, e.getMessage());
        }
    }

    @Override
    public VoucherValidation validateVoucher(String tenantId, String voucherPin) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("Airtel Gateway not configured for tenant={}", tenantId);
            return new VoucherValidation(false, voucherPin, null, "LKR", null, "No Airtel Gateway integration");
        }

        var queryParams = Map.<String, Object>of("voucherPin", voucherPin);
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg, "/v1/osp/recharge/voucher/validate", queryParams, null);
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
        } catch (AirtelApiException e) {
            log.error("Airtel validateVoucher failed for tenant={}: {}", tenantId, e.getMessage());
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
