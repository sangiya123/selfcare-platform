package com.selfcare.airtel.provider;

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
 * Airtel Auth Provider — integrates with Airtel Lanka's Money Gateway auth APIs.
 *
 * <p>Implements the canonical {@link AuthProvider} contract from platform-common.
 * Airtel uses a combination of OAuth2-style client credentials for the gateway
 * plus API-key for session operations. The OAuth2 token is managed by
 * {@link AirtelHttpClient}.</p>
 *
 * <p>Per-tenant config (gateway base URL, client credentials) is loaded at
 * runtime via {@link AirtelHttpClient} which reads
 * {@code TenantConfigurationService} — NOT from env files.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = AuthProvider.class)
@RequiredArgsConstructor
public class AirtelAuthProvider implements ApiAdapter, AuthProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_GATEWAY";
    private static final String ADAPTER_ID = "airtel-lk";

    private final AirtelHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // OTP
    // ================================================================

    @Override
    public OtpSendResult sendOtp(String tenantId, String msisdn, String channel) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("No Airtel Gateway integration configured for tenant")
                    .build();
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn);
        body.put("channel", channel != null ? channel : "SMS");

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/auth/otp", body, token);
            if (resp == null) {
                return OtpSendResult.builder().success(false)
                        .failureReason("Empty response from Airtel Gateway").build();
            }
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return OtpSendResult.builder()
                        .success(true)
                        .correlationId((String) data.get("correlationId"))
                        .expiresInSeconds(data.get("ttlSeconds") instanceof Number n ? n.intValue() : 300)
                        .build();
            }
            return OtpSendResult.builder().success(false)
                    .failureReason("Unexpected Airtel Gateway response shape").build();
        } catch (AirtelApiException e) {
            log.error("Airtel OTP send failed for {}: {}", msisdn, e.getMessage());
            return OtpSendResult.builder()
                    .success(false)
                    .failureReason("Airtel Gateway error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public OtpVerifyResult verifyOtp(String tenantId, String msisdn, String correlationId, String otpCode) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("No Airtel Gateway integration configured for tenant")
                    .build();
        }

        String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn);
        body.put("correlationId", correlationId);
        body.put("otp", otpCode);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/auth/otp/verify", body, token);
            if (resp == null) {
                return OtpVerifyResult.builder().success(false)
                        .failureReason("Empty response from Airtel Gateway").build();
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
            return OtpVerifyResult.builder().success(false).failureReason("Invalid OTP").build();
        } catch (AirtelApiException e) {
            log.error("Airtel OTP verify failed for {}: {}", msisdn, e.getMessage());
            return OtpVerifyResult.builder()
                    .success(false)
                    .failureReason("Airtel Gateway error: " + e.getMessage())
                    .build();
        }
    }

    // ================================================================
    // Session
    // ================================================================

    @Override
    public SessionValidationResult validateSession(String tenantId, String accessToken) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return SessionValidationResult.builder()
                    .active(false)
                    .failureReason("No Airtel Gateway integration configured for tenant")
                    .build();
        }
        try {
            Map<String, Object> resp = httpClient.get(tenantId, cfg,
                    "/v1/osp/auth/session", null, accessToken);
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
        } catch (AirtelApiException e) {
            return SessionValidationResult.builder()
                    .active(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }

    @Override
    public TokenRefreshResult refreshToken(String tenantId, String refreshToken) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason("No Airtel Gateway integration configured for tenant")
                    .build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("grantType", "refresh_token");
        body.put("refreshToken", refreshToken);

        try {
            Map<String, Object> resp = httpClient.post(tenantId, cfg,
                    "/v1/osp/auth/token/refresh", body, null);
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
        } catch (AirtelApiException e) {
            return TokenRefreshResult.builder()
                    .success(false)
                    .failureReason(e.getMessage())
                    .build();
        }
    }
}
