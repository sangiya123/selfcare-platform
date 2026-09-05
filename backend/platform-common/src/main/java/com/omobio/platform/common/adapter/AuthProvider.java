package com.omobio.platform.common.adapter;

import java.util.Date;

/**
 * Auth/identity provider contract for telco (and other) industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose the canonical authentication capabilities used by the customer
 * identity service and the BFFs:
 *
 * <ul>
 *   <li>Send / verify OTP (SMS-based passwordless login)</li>
 *   <li>Validate an active session / access token</li>
 *   <li>Refresh an expired access token</li>
 *   <li>Authorization-code exchange (MIFE-style OAuth2)</li>
 * </ul>
 *
 * All methods take {@code tenantId} as the first argument so a single
 * implementation can serve many tenants (or so the registry can route
 * per-tenant). Upstream config (URL, credentials) is loaded at runtime
 * from {@link com.omobio.platform.common.tenant.TenantConfigurationService}.
 */
public interface AuthProvider extends ApiAdapter {

    /**
     * Send a one-time password to the given MSISDN.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param msisdn   E.164 phone number of the subscriber
     * @param channel  delivery channel — {@code "SMS"} (default), {@code "VOICE"}, etc.
     */
    OtpSendResult sendOtp(String tenantId, String msisdn, String channel);

    /**
     * Verify an OTP submitted by the subscriber.
     *
     * @param tenantId       the tenant identifier
     * @param msisdn         the subscriber's MSISDN
     * @param correlationId  the id returned by {@link #sendOtp}
     * @param otpCode        the OTP entered by the user
     */
    OtpVerifyResult verifyOtp(String tenantId, String msisdn, String correlationId, String otpCode);

    /**
     * Validate an existing access token (e.g. on each authenticated request).
     */
    SessionValidationResult validateSession(String tenantId, String accessToken);

    /**
     * Refresh an expired access token using a refresh token.
     */
    TokenRefreshResult refreshToken(String tenantId, String refreshToken);

    // ================================================================
    // Result DTOs
    // ================================================================

    /**
     * Result of an OTP-send operation.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class OtpSendResult {
        private boolean success;
        private String correlationId;
        private Integer expiresInSeconds;
        private String failureReason;
    }

    /**
     * Result of an OTP-verify operation. On success, carries the access
     * and refresh tokens issued by the operator.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class OtpVerifyResult {
        private boolean success;
        private String connectionId;
        private String accessToken;
        private String refreshToken;
        private Integer expiresInSeconds;
        private String failureReason;
    }

    /**
     * Result of validating an access token.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class SessionValidationResult {
        private boolean active;
        private String subscriberId;
        private String scope;
        private Date expiresAt;
        private String failureReason;
    }

    /**
     * Result of a token-refresh operation.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class TokenRefreshResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private Integer expiresInSeconds;
        private String tokenType;
        private String failureReason;
    }
}
