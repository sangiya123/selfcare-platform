package com.omobio.identity.service;

import com.omobio.identity.domain.Session;
import com.omobio.identity.repository.SessionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer session service — manages session lifecycle.
 *
 * Implements:
 * - Session creation on login
 * - Refresh token rotation with replay detection
 * - Session listing / remote logout
 * - Last activity update
 * - Step-up auth hook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerSessionService {

    private final SessionRepository sessionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${omobio.security.session.refresh-ttl-seconds:2592000}") // 30 days default
    private long refreshTtlSeconds;

    @Value("${omobio.security.session.inactivity-timeout-seconds:1800}") // 30 min default
    private long inactivityTimeoutSeconds;

    private static final String SESSION_CACHE_PREFIX = "omobio:session:";

    /**
     * Create a new session for a user.
     */
    @Transactional
    public Session createSession(String userId, String deviceId, String deviceDescription, String ipAddress) {
        String tenantId = TenantContext.get().getTenantId();

        Session session = Session.builder()
                .sessionId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .tokenFamilyId(UUID.randomUUID().toString())
                .deviceId(deviceId)
                .deviceDescription(deviceDescription)
                .ipAddress(ipAddress)
                .status("ACTIVE")
                .issuedAt(Instant.now())
                .lastUsedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(refreshTtlSeconds))
                .build();

        session = sessionRepository.save(session);

        // Cache in Redis for fast access
        cacheSession(session);

        log.info("Session created: sessionId={}, userId={}, deviceId={}",
                session.getSessionId(), userId, deviceId);
        return session;
    }

    /**
     * Rotate a refresh token.
     * Detects replay attacks: if the previous token is reused, revoke the entire token family.
     */
    @Transactional
    public Session rotateRefreshToken(String sessionId, String newRefreshToken, String previousRefreshToken) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session not found"));

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new UnauthorizedException("Session is not active: " + session.getStatus());
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus("EXPIRED");
            sessionRepository.save(session);
            throw new UnauthorizedException("Session expired");
        }

        String newTokenHash = hashToken(newRefreshToken);
        String previousTokenHash = hashToken(previousRefreshToken);

        // Replay detection: if previous token hash matches what's stored as previous,
        // the previous refresh was already used. This means a replay attack.
        if (previousTokenHash.equals(session.getPreviousTokenHash())) {
            log.warn("REPLAY DETECTED: previous token reused for session {}", sessionId);
            revokeTokenFamily(session.getTokenFamilyId(), "REPLAY_DETECTED");
            throw new UnauthorizedException("Token replay detected, all sessions in family revoked");
        }

        // Rotate: shift current -> previous, new -> current
        session.setPreviousTokenHash(session.getCurrentTokenHash());
        session.setCurrentTokenHash(newTokenHash);
        session.setLastUsedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(refreshTtlSeconds));

        session = sessionRepository.save(session);
        cacheSession(session);

        log.debug("Token rotated: sessionId={}, familyId={}", sessionId, session.getTokenFamilyId());
        return session;
    }

    /**
     * Revoke a session (logout).
     */
    @Transactional
    public void revokeSession(String sessionId, String reason) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session not found"));
        revokeSession(session, reason);
    }

    /**
     * Revoke all sessions in a token family (used on replay detection).
     */
    @Transactional
    public void revokeTokenFamily(String tokenFamilyId, String reason) {
        List<Session> sessions = sessionRepository.findByTokenFamilyId(tokenFamilyId);
        for (Session session : sessions) {
            revokeSession(session, reason);
        }
        log.warn("Revoked {} sessions in family {} (reason: {})",
                sessions.size(), tokenFamilyId, reason);
    }

    /**
     * List all active sessions for a user.
     */
    public List<Session> getActiveSessions(String userId) {
        return sessionRepository.findByUserIdAndStatus(userId, "ACTIVE");
    }

    /**
     * Update last activity timestamp (throttled).
     */
    public void updateLastActivity(String sessionId) {
        String cacheKey = SESSION_CACHE_PREFIX + sessionId + ":lastActivity";
        String lastUpdate = redisTemplate.opsForValue().get(cacheKey);

        long now = Instant.now().getEpochSecond();
        long lastUpdateTs = lastUpdate != null ? Long.parseLong(lastUpdate) : 0;

        // Throttle: only update once per minute
        if (now - lastUpdateTs > 60) {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(now), Duration.ofMinutes(5));

            // Update DB asynchronously
            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setLastUsedAt(Instant.now());
                sessionRepository.save(session);
            });
        }
    }

    private void revokeSession(Session session, String reason) {
        session.setStatus("REVOKED");
        session.setRevokedAt(Instant.now());
        session.setRevokedReason(reason);
        sessionRepository.save(session);

        // Remove from cache
        redisTemplate.delete(SESSION_CACHE_PREFIX + session.getSessionId());
    }

    private void cacheSession(Session session) {
        String key = SESSION_CACHE_PREFIX + session.getSessionId();
        // Store minimal session data in Redis
        redisTemplate.opsForValue().set(key, session.getUserId(),
                Duration.ofSeconds(inactivityTimeoutSeconds));
    }

    /**
     * Return the underlying repository (used by AuthController for cross-check).
     * Exposes only what is needed; callers must not mutate records directly.
     */
    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}