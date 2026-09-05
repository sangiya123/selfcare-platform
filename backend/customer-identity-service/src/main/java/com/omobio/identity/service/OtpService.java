package com.omobio.identity.service;

import com.omobio.identity.domain.OtpCode;
import com.omobio.identity.repository.OtpRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * OTP lifecycle service — generate, dispatch, verify, lockout.
 *
 * <p>OTP flow:
 * <ol>
 *   <li>Client calls POST /api/v1/auth/otp with identifier + channel
 *   <li>generateOtp() creates a new OTP record (hashed code), dispatches it
 *       asynchronously via NotificationService, and returns a correlationId.
 *   <li>Client calls POST /api/v1/auth/otp/verify with identifier + code + correlationId
 *   <li>verifyOtp() looks up by correlationId, checks expiry and attempt count,
 *       marks USED on success, returns a session with JWT tokens.
 * </ol>
 *
 * <p>Lockout: after 5 failed attempts the OTP transitions to LOCKED status
 * and a new OTP must be requested. A lockout penalty window (30 minutes)
 * is enforced before a fresh code is accepted.
 *
 * <p>Token policy: ADR-011 (open). The current implementation uses conservative
 * defaults pending a security review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient.Builder webClientBuilder;

    @Value("${omobio.security.otp.ttl-seconds:300}")
    private int otpTtlSeconds;

    @Value("${omobio.security.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${omobio.security.otp.lockout-seconds:1800}")
    private int lockoutSeconds;

    @Value("${omobio.security.otp.code-length:6}")
    private int codeLength;

    @Value("${omobio.notification.service.url:http://notification-service:8090}")
    private String notificationServiceUrl;

    private static final String LOCKOUT_KEY_PREFIX = "omobio:otp:lockout:";
    private static final String OTP_CODE_CHARS = "0123456789";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate and dispatch a new OTP for the given identifier and channel.
     *
     * <p>Any previously active OTP for the same tenant+identifier is immediately
     * expired before a new one is issued (prevents code-stuffing).
     *
     * @param tenantId   the tenant ID (required; set from TenantContext if null)
     * @param identifier MSISDN, email address, or customer ID
     * @param channel    delivery channel: SMS, EMAIL, or PUSH
     * @return generation result containing correlationId and expiry instant
     * @throws BadRequestException if the channel is invalid or the identifier
     *                             is already in a lockout window
     */
    @Transactional
    public OtpGenerateResult generateOtp(String tenantId, String identifier, String channel) {
        tenantId = resolveTenantId(tenantId);
        validateChannel(channel);

        // Check lockout
        if (isLockedOut(tenantId, identifier)) {
            throw new BadRequestException("Too many failed attempts. Please try again later.");
        }

        // Expire any outstanding OTPs for this identifier to prevent accumulation
        int expired = otpRepository.expireActiveByTenantAndIdentifier(
                tenantId, identifier, OtpCode.STATUS_ACTIVE);
        if (expired > 0) {
            log.debug("Expired {} stale OTP(s) for tenant={}, identifier={}",
                    expired, tenantId, maskIdentifier(identifier));
        }

        // Generate random numeric code and compute hash
        String rawCode = generateNumericCode(codeLength);
        String codeHash = hashCode(rawCode);

        String correlationId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(otpTtlSeconds);

        // Persist the hashed OTP
        OtpCode otp = OtpCode.builder()
                .otpId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .identifier(identifier)
                .codeHash(codeHash)
                .channel(channel)
                .status(OtpCode.STATUS_ACTIVE)
                .attemptCount(0)
                .expiresAt(expiresAt)
                .correlationId(correlationId)
                .build();

        otpRepository.save(otp);

        log.info("OTP generated: tenant={}, identifier={}, channel={}, expiresAt={}, correlationId={}",
                tenantId, maskIdentifier(identifier), channel, expiresAt, correlationId);

        // Dispatch asynchronously to the notification service
        dispatchOtpAsync(tenantId, identifier, rawCode, channel, correlationId);

        return new OtpGenerateResult(correlationId, expiresAt, otpTtlSeconds);
    }

    /**
     * Verify an OTP code.
     *
     * <p>Accepts either {@code identifier} + {@code code} (legacy lookup by
     * MSISDN/email) or a {@code correlationId} to disambiguate between multiple
     * active codes for the same identifier.
     *
     * <p>On failure: increments attemptCount, checks lockout threshold.
     * On success: marks OTP USED, clears any Redis lockout.
     *
     * @param tenantId      the tenant ID
     * @param identifier    MSISDN, email, or customer ID (used if correlationId is null)
     * @param rawCode       the plain-text OTP entered by the user
     * @param correlationId optional correlation ID from generate response
     * @return the verified OtpCode record (caller extracts user info)
     * @throws UnauthorizedException if the code is wrong, expired, or locked
     */
    @Transactional
    public OtpCode verifyOtp(String tenantId, String identifier, String rawCode, String correlationId) {
        tenantId = resolveTenantId(tenantId);

        OtpCode otp = findOtpForVerification(tenantId, identifier, correlationId);

        // Check lockout window (Redis)
        if ("LOCKED".equals(otp.getStatus())) {
            throw new UnauthorizedException("Too many failed attempts. Please request a new OTP.");
        }
        if (isLockedOut(tenantId, identifier)) {
            throw new UnauthorizedException("Too many failed attempts. Please request a new OTP.");
        }

        // Expiry check
        if (Instant.now().isAfter(otp.getExpiresAt())) {
            markExpired(otp);
            throw new UnauthorizedException("OTP has expired. Please request a new one.");
        }

        // Code comparison (constant-time via hash)
        if (!hashCode(rawCode).equals(otp.getCodeHash())) {
            handleFailedAttempt(tenantId, identifier, otp);
            throw new UnauthorizedException("Invalid OTP code");
        }

        // Success
        otp.setStatus(OtpCode.STATUS_USED);
        otp.setUsedAt(Instant.now());
        otp.setAttemptCount(otp.getAttemptCount() + 1);
        otpRepository.save(otp);

        clearLockout(tenantId, identifier);

        log.info("OTP verified successfully: tenant={}, identifier={}, correlationId={}",
                tenantId, maskIdentifier(identifier), otp.getCorrelationId());

        return otp;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Find the OTP record for verification.
     * Prefers correlationId lookup (unambiguous); falls back to identifier+latest-active.
     */
    private OtpCode findOtpForVerification(String tenantId, String identifier, String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return otpRepository.findByCorrelationId(correlationId)
                    .orElseThrow(() -> new UnauthorizedException("Invalid correlation ID"));
        }
        if (identifier != null && !identifier.isBlank()) {
            return otpRepository.findLatestActiveByTenantAndIdentifier(
                            tenantId, identifier, OtpCode.STATUS_ACTIVE)
                    .orElseThrow(() -> new UnauthorizedException("No active OTP found for this identifier"));
        }
        throw new BadRequestException("Either identifier or correlationId is required");
    }

    private void handleFailedAttempt(String tenantId, String identifier, OtpCode otp) {
        int attempts = otp.getAttemptCount() + 1;
        otp.setAttemptCount(attempts);
        otpRepository.save(otp);

        log.warn("OTP verify failed: tenant={}, identifier={}, attempt={}/{}",
                tenantId, maskIdentifier(identifier), attempts, maxAttempts);

        if (attempts >= maxAttempts) {
            // Mark DB record as locked
            otp.setStatus(OtpCode.STATUS_LOCKED);
            otpRepository.save(otp);
            // Set Redis lockout key
            setLockout(tenantId, identifier);
            log.warn("OTP locked out: tenant={}, identifier={}", tenantId, maskIdentifier(identifier));
            throw new UnauthorizedException("Too many failed attempts. Please request a new OTP.");
        }
    }

    private boolean isLockedOut(String tenantId, String identifier) {
        String key = LOCKOUT_KEY_PREFIX + tenantId + ":" + identifier;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void setLockout(String tenantId, String identifier) {
        String key = LOCKOUT_KEY_PREFIX + tenantId + ":" + identifier;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(lockoutSeconds));
    }

    private void clearLockout(String tenantId, String identifier) {
        String key = LOCKOUT_KEY_PREFIX + tenantId + ":" + identifier;
        redisTemplate.delete(key);
    }

    private void markExpired(OtpCode otp) {
        otp.setStatus(OtpCode.STATUS_EXPIRED);
        otpRepository.save(otp);
    }

    private String resolveTenantId(String tenantId) {
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.get().getTenantId();
    }

    private void validateChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new BadRequestException("Channel is required: SMS, EMAIL, or PUSH");
        }
        if (!channel.equalsIgnoreCase(OtpCode.CHANNEL_SMS)
                && !channel.equalsIgnoreCase(OtpCode.CHANNEL_EMAIL)
                && !channel.equalsIgnoreCase(OtpCode.CHANNEL_PUSH)) {
            throw new BadRequestException("Unsupported channel: " + channel + ". Supported: SMS, EMAIL, PUSH");
        }
    }

    /**
     * Dispatch the OTP via the notification service REST API.
     * Failures here are logged but do not fail the generate call — the OTP
     * is already stored in the DB and can be re-dispatched on request.
     */
    private void dispatchOtpAsync(String tenantId, String identifier, String rawCode,
                                   String channel, String correlationId) {
        try {
            Map<String, Object> payload = Map.of(
                    "code", rawCode,
                    "correlationId", correlationId,
                    "ttlSeconds", otpTtlSeconds
            );

            String templateId = switch (channel.toUpperCase()) {
                case "SMS" -> "otp_sms";
                case "EMAIL" -> "otp_email";
                case "PUSH" -> "otp_push";
                default -> "otp_generic";
            };

            String recipient = "PUSH".equals(channel.toUpperCase()) ? null : identifier;

            webClientBuilder.build()
                    .post()
                    .uri(notificationServiceUrl + "/api/v1/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "channel", channel.toUpperCase(),
                            "recipient", recipient != null ? recipient : "",
                            "templateId", templateId,
                            "payload", payload,
                            "userId", "OTP_FLOW:" + correlationId,
                            "locale", "en"
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            resp -> log.debug("OTP dispatch confirmed: correlationId={}", correlationId),
                            ex -> log.warn("OTP dispatch failed: correlationId={}, error={}",
                                    correlationId, ex.getMessage())
                    );

        } catch (Exception e) {
            // Log but do not fail — OTP is in DB and can be re-sent
            log.error("OTP dispatch threw unexpectedly: correlationId={}", correlationId, e);
        }
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            sb.append(OTP_CODE_CHARS.charAt(rnd.nextInt(OTP_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Mask identifier for logging (privacy). Shows first 3 and last 3 chars.
     */
    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() <= 6) return "***";
        return identifier.substring(0, 3) + "***" + identifier.substring(identifier.length() - 3);
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Result of an OTP generate call — returned to the client.
     *
     * @param correlationId  opaque ID for the verify call
     * @param expiresAt     instant when the code expires
     * @param expiresInSeconds seconds until expiry (convenience)
     */
    public record OtpGenerateResult(
            String correlationId,
            Instant expiresAt,
            int expiresInSeconds
    ) {}
}
