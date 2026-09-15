package com.selfcare.hutch.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.AuthProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Hutch Auth Provider — integrates with Hutch Sri Lanka's BSS auth APIs.
 *
 * <p>Implements the canonical {@link AuthProvider} contract from
 * platform-common. Hutch uses simple API-key authentication rather than
 * OAuth2 — the API key is sent as the {@code X-API-Key} header on every
 * request.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded at runtime via
 * {@link HutchHttpClient} which reads
 * {@code TenantConfigurationService} — NOT from env files.
 * Configure via Selfcare Studio admin: Integrations &gt; Hutch BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = AuthProvider.class)
@RequiredArgsConstructor
public class HutchAuthProvider implements ApiAdapter, AuthProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // OTP
    // ================================================================

    @Override
    public OtpSendResult sendOtp(String tenantId, String msisdn, String channel) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("No Hutch BSS integration configured for tenant")
                    .build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn);
        body.put("channel", channel != null ? channel : "SMS");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/api/v1/auth/otp/send", body, null);
            if (resp == null) {
                return OtpSendResult.builder()
                        .success(false)
                        .failureReason("Empty response from Hutch BSS")
                        .build();
            }
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return OtpSendResult.builder()
                        .success(true)
                        .correlationId((String) data.get("correlationId"))
                        .expiresInSeconds(data.get("expiresIn") instanceof Number n ? n.intValue() : 300)
                        .build();
            }
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("Hutch BSS returned unexpected response shape")
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch OTP send failed for {}: {}", msisdn, e.getMessage());
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("Hutch BSS error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public OtpVerifyResult verifyOtp(String tenantId, String msisdn, String correlationId, String otpCode) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("No Hutch BSS integration configured for tenant")
                    .build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn);
        body.put("correlationId", correlationId);
        body.put("otp", otpCode);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/api/v1/auth/otp/verify", body, null);
            if (resp == null) {
                return OtpVerifyResult.builder()
                        .success(false)
                        .failureReason("Empty response from Hutch BSS")
                        .build();
            }
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return OtpVerifyResult.builder()
                        .success(true)
                        .connectionId((String) data.get("connectionId"))
                        .accessToken((String) data.get("accessToken"))
                        .refreshToken((String) data.get("refreshToken"))
                        .expiresInSeconds(data.get("expiresIn") instanceof Number n ? n.intValue() : 3600)
                        .build();
            }
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("Invalid OTP")
                    .build();
        } catch (HutchApiException e) {
            log.error("Hutch OTP verify failed for {}: {}", msisdn, e.getMessage());
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("Hutch BSS error: " + e.getMessage())
                    .build();
        }
    }

    // ================================================================
    // Session
    // ================================================================

    @Override
    public SessionValidationResult validateSession(String tenantId, String accessToken) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return SessionValidationResult.builder()
                    .active(false)
                    .failureReason("No Hutch BSS integration configured for tenant")
                    .build();
        }
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg,
                    "/api/v1/auth/session/validate", null, accessToken);
            if (resp == null) {
                return SessionValidationResult.builder().active(false).build();
            }
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                boolean active = Boolean.TRUE.equals(data.get("active"));
                return SessionValidationResult.builder()
                        .active(active)
                        .subscriberId((String) data.get("subscriberId"))
                        .scope((String) data.get("scope"))
                        .expiresAt(data.get("exp") instanceof Number n
                                ? new Date(n.longValue() * 1000L)
                                : null)
                        .build();
            }
            return SessionValidationResult.builder().active(false).build();
        } catch (HutchApiException e) {
            return SessionValidationResult.builder()
                    .active(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }

    @Override
    public TokenRefreshResult refreshToken(String tenantId, String refreshToken) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason("No Hutch BSS integration configured for tenant")
                    .build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("refreshToken", refreshToken);
        body.put("grantType", "refresh_token");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/api/v1/auth/token/refresh", body, null);
            if (resp == null || resp.get("data") == null) {
                return TokenRefreshResult.builder().success(false).failureReason("Empty response").build();
            }
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return TokenRefreshResult.builder()
                        .success(true)
                        .accessToken((String) data.get("accessToken"))
                        .refreshToken((String) data.get("refreshToken"))
                        .expiresInSeconds(data.get("expiresIn") instanceof Number n ? n.intValue() : null)
                        .tokenType((String) data.getOrDefault("tokenType", "Bearer"))
                        .build();
            }
            return TokenRefreshResult.builder().success(false).failureReason("Unexpected response shape").build();
        } catch (HutchApiException e) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }
}
