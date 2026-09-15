package com.selfcare.platform.common.contract.auth;

import java.time.Instant;

/**
 * Canonical customer authentication contract. Industrial-neutral: a credential may be an MSISDN
 * (telco), policy/customer number (insurance), or partner reference — the payload is opaque here.
 */
public final class CustomerAuthContract {

    private CustomerAuthContract() {}

    public record LoginRequest(
            String tenantId,
            String channel,
            String userId,
            String credential,
            String deviceId,
            String locale) {
    }

    public record OtpRequest(
            String tenantId,
            String channel,
            String userId,
            String purpose) {
    }

    public record OtpVerifyRequest(
            String otpId,
            String code,
            String deviceId) {
    }

    public record AuthSession(
            String sessionId,
            String userId,
            String status,
            boolean mfaRequired,
            Instant issuedAt,
            Instant expiresAt) {
    }

    public record AuthTokens(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            long refreshExpiresInSeconds,
            String sessionId) {
    }

    public interface CustomerAuthService {
        AuthSession initiate(String mode, OtpRequest request);

        AuthSession verifyOtp(OtpVerifyRequest request);

        AuthTokens issueTokens(AuthSession session);
    }
}