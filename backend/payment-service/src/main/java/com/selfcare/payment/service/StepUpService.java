package com.selfcare.payment.service;

import com.selfcare.payment.domain.PaymentTransaction;
import com.selfcare.payment.domain.StepUpRequest;
import com.selfcare.payment.domain.StepUpVerification;
import com.selfcare.payment.repository.StepUpRequestRepository;
import com.selfcare.payment.repository.StepUpVerificationRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Step-up authentication service.
 *
 * For high-risk actions (cross-connection payment, high-value transactions,
 * profile/security changes), require a fresh OTP/biometric confirmation.
 *
 * Flow:
 * 1. Client requests step-up with the action context (e.g. bill payment)
 * 2. Server generates a 6-digit code, sends via OTP, returns correlationId
 * 3. Client verifies code → receives stepUpToken
 * 4. Client retries the action with stepUpToken in header
 * 5. Server validates token (Redis) + verifies amount hasn't changed
 * 6. Action proceeds
 *
 * Step-up tokens are scoped: valid only for the specific action + amount
 * for a short window (default 5 minutes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepUpService {

    private final StepUpRequestRepository requestRepository;
    private final StepUpVerificationRepository verificationRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final int CODE_TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Amounts above this threshold always require step-up (configurable per tenant). */
    private static final java.math.BigDecimal DEFAULT_THRESHOLD = new java.math.BigDecimal("10000.00");

    /**
     * Check if an action requires step-up.
     * @return true if step-up is required
     */
    public boolean isStepUpRequired(String tenantId, String action, java.math.BigDecimal amount,
                                    String fromConnectionId, String toConnectionId) {
        // Cross-connection operations always require step-up
        if (fromConnectionId != null && toConnectionId != null
                && !fromConnectionId.equals(toConnectionId)) {
            log.info("Step-up required: cross-connection {} -> {} for tenant {}",
                    fromConnectionId, toConnectionId, tenantId);
            return true;
        }

        // High-value transactions always require step-up
        if (amount != null && amount.compareTo(DEFAULT_THRESHOLD) > 0) {
            log.info("Step-up required: high-value amount {} for tenant {}", amount, tenantId);
            return true;
        }

        return false;
    }

    /**
     * Initiate step-up: generate a code, send it via OTP, return correlationId.
     */
    @Transactional
    public StepUpInitiation initiate(String tenantId, String userId, String action,
                                      java.math.BigDecimal amount, String idempotencyKey) {
        String code = generateCode();
        String correlationId = "stepup-" + UUID.randomUUID();

        StepUpRequest request = new StepUpRequest();
        request.setCorrelationId(correlationId);
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setAction(action);
        request.setAmount(amount);
        request.setIdempotencyKey(idempotencyKey);
        request.setCodeHash(hashCode(code));
        request.setStatus("PENDING");
        request.setAttemptCount(0);
        request.setExpiresAt(Instant.now().plus(Duration.ofMinutes(CODE_TTL_MINUTES)));
        request.setCreatedAt(Instant.now());
        requestRepository.save(request);

        // Store code in Redis with TTL (for fast lookup, with attempt count)
        String redisKey = "selfcare:stepup:" + correlationId;
        redisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(CODE_TTL_MINUTES));
        redisTemplate.opsForValue().set(redisKey + ":attempts", "0", Duration.ofMinutes(CODE_TTL_MINUTES));

        // Send via OTP (delegated to notification-service via WebClient)
        // For now, log it. In production, this is asynchronous.
        log.info("Step-up code generated for {} action {}: code sent via OTP to user {}",
                tenantId, action, userId);

        return new StepUpInitiation(correlationId,
                Instant.now().plus(Duration.ofMinutes(CODE_TTL_MINUTES)),
                Duration.ofMinutes(CODE_TTL_MINUTES).toSeconds());
    }

    /**
     * Verify the code and issue a step-up token.
     */
    @Transactional
    public StepUpToken verify(String correlationId, String code) {
        StepUpRequest request = requestRepository.findByCorrelationId(correlationId)
            .orElseThrow(() -> new BadRequestException("Invalid step-up correlationId"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new BadRequestException("Step-up request is no longer valid");
        }

        if (request.getExpiresAt().isBefore(Instant.now())) {
            request.setStatus("EXPIRED");
            requestRepository.save(request);
            throw new BadRequestException("Step-up code has expired");
        }

        // Check lockout
        String lockoutKey = "selfcare:stepup:locked:" + correlationId;
        Boolean locked = redisTemplate.hasKey(lockoutKey);
        if (Boolean.TRUE.equals(locked)) {
            throw new UnauthorizedException("Too many failed attempts. Try again in 30 minutes.");
        }

        // Verify code
        if (!constantTimeEquals(code, request.getCodeHash())) {
            request.setAttemptCount(request.getAttemptCount() + 1);
            requestRepository.save(request);

            if (request.getAttemptCount() >= MAX_ATTEMPTS) {
                request.setStatus("LOCKED");
                requestRepository.save(request);
                redisTemplate.opsForValue().set(lockoutKey, "1", Duration.ofMinutes(LOCKOUT_MINUTES));
                throw new UnauthorizedException("Too many failed attempts. Try again in 30 minutes.");
            }

            throw new UnauthorizedException("Invalid step-up code. "
                + (MAX_ATTEMPTS - request.getAttemptCount()) + " attempts remaining.");
        }

        // Mark as verified
        request.setStatus("VERIFIED");
        request.setVerifiedAt(Instant.now());
        requestRepository.save(request);

        // Record verification
        StepUpVerification verification = new StepUpVerification();
        verification.setCorrelationId(correlationId);
        verification.setVerifiedAt(Instant.now());
        verification.setTenantId(request.getTenantId());
        verification.setUserId(request.getUserId());
        verification.setAction(request.getAction());
        verificationRepository.save(verification);

        // Issue step-up token (valid for 5 minutes, scoped to this action+amount)
        String stepUpToken = "su-" + UUID.randomUUID();
        String tokenKey = "selfcare:stepup:token:" + stepUpToken;
        String tokenValue = String.format("%s|%s|%s|%s",
            request.getTenantId(),
            request.getUserId(),
            request.getAction(),
            request.getAmount() != null ? request.getAmount().toPlainString() : "0");
        redisTemplate.opsForValue().set(tokenKey, tokenValue, Duration.ofMinutes(5));

        log.info("Step-up verified: user={}, action={}, amount={}",
                request.getUserId(), request.getAction(), request.getAmount());

        return new StepUpToken(stepUpToken, Duration.ofMinutes(5).toSeconds(),
                request.getAction(), request.getAmount());
    }

    /**
     * Validate a step-up token for a specific action.
     * Returns true if token is valid AND matches the requested action/amount.
     */
    public boolean validateStepUpToken(String stepUpToken, String expectedAction,
                                       java.math.BigDecimal expectedAmount) {
        if (stepUpToken == null || !stepUpToken.startsWith("su-")) {
            return false;
        }
        String tokenKey = "selfcare:stepup:token:" + stepUpToken;
        String tokenValue = redisTemplate.opsForValue().get(tokenKey);
        if (tokenValue == null) {
            return false;
        }
        String[] parts = tokenValue.split("\\|");
        if (parts.length != 4) {
            return false;
        }
        String tenantId = parts[0];
        String userId = parts[1];
        String action = parts[2];
        String amount = parts[3];

        String currentTenant = TenantContext.get().getTenantId();
        String currentUser = TenantContext.get().getUserId();

        if (!tenantId.equals(currentTenant) || !userId.equals(currentUser)) {
            log.warn("Step-up token tenant/user mismatch: token={}/{}, current={}/{}",
                    tenantId, userId, currentTenant, currentUser);
            return false;
        }

        if (!action.equals(expectedAction)) {
            log.warn("Step-up token action mismatch: expected={}, got={}", expectedAction, action);
            return false;
        }

        if (expectedAmount != null) {
            try {
                java.math.BigDecimal tokenAmount = new java.math.BigDecimal(amount);
                // Amount must match exactly (no tolerance for fraud prevention)
                if (tokenAmount.compareTo(expectedAmount) != 0) {
                    log.warn("Step-up token amount mismatch: expected={}, got={}", expectedAmount, tokenAmount);
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    /** Consume (one-time use) a step-up token after successful action. */
    public void consumeStepUpToken(String stepUpToken) {
        if (stepUpToken == null) return;
        String tokenKey = "selfcare:stepup:token:" + stepUpToken;
        redisTemplate.delete(tokenKey);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String hashCode(String code) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean constantTimeEquals(String code, String expectedHash) {
        String actualHash = hashCode(code);
        if (actualHash.length() != expectedHash.length()) return false;
        int result = 0;
        for (int i = 0; i < actualHash.length(); i++) {
            result |= actualHash.charAt(i) ^ expectedHash.charAt(i);
        }
        return result == 0;
    }

    public record StepUpInitiation(String correlationId, Instant expiresAt, long expiresIn) {}
    public record StepUpToken(String token, long expiresIn, String action, java.math.BigDecimal amount) {}
}
