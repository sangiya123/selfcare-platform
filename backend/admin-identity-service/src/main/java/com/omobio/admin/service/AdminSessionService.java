package com.omobio.admin.service;

import com.omobio.admin.domain.AdminSession;
import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminSessionRepository;
import com.omobio.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin session service — manages session lifecycle.
 *
 * Implements the same pattern as {@code CustomerSessionService}:
 * - Session creation on login
 * - Refresh token rotation with replay detection
 * - Session listing / remote logout
 * - Last activity update
 *
 * @see AdminAuthService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSessionService {

    private final AdminSessionRepository sessionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${omobio.security.session.refresh-ttl-seconds:2592000}")
    private long refreshTtlSeconds;

    @Value("${omobio.security.session.inactivity-timeout-seconds:1800}")
    private long inactivityTimeoutSeconds;

    private static final String SESSION_CACHE_PREFIX = "omobio:admin:session:";

    /**
     * Create a new admin session.
     */
    @Transactional
    public AdminSession createSession(AdminUser user, String deviceId,
                                      String ipAddress, String userAgent) {
        String tenantId = user.getTenantId();
        AdminSession session = AdminSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .adminUserId(user.getId())
                .tenantId(tenantId)
                .role(user.getRole())
                .tokenFamilyId(UUID.randomUUID().toString())
                .deviceId(deviceId)
                .status("ACTIVE")
                .issuedAt(Instant.now())
                .lastUsedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(refreshTtlSeconds))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        session = sessionRepository.save(session);
        cacheSession(session);

        log.info("Admin session created: sessionId={}, userId={}", session.getSessionId(), user.getId());
        return session;
    }

    /**
     * Revoke a single session.
     */
    @Transactional
    public void revokeSession(String sessionId, String reason) {
        AdminSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Session not found"));
        session.setStatus("REVOKED");
        session.setRevokedAt(Instant.now());
        session.setRevokedReason(reason);
        sessionRepository.save(session);
        redisTemplate.delete(SESSION_CACHE_PREFIX + sessionId);
        log.info("Admin session revoked: sessionId={}, reason={}", sessionId, reason);
    }

    /**
     * Revoke all sessions in a token family (replay detection).
     */
    @Transactional
    public void revokeTokenFamily(String tokenFamilyId, String reason) {
        List<AdminSession> sessions = sessionRepository.findByTokenFamilyId(tokenFamilyId);
        for (AdminSession session : sessions) {
            session.setStatus("REVOKED");
            session.setRevokedAt(Instant.now());
            session.setRevokedReason(reason);
            sessionRepository.save(session);
            redisTemplate.delete(SESSION_CACHE_PREFIX + session.getSessionId());
        }
        log.warn("Revoked {} admin sessions in family {} (reason: {})",
                sessions.size(), tokenFamilyId, reason);
    }

    /**
     * Revoke all sessions for a user (e.g. on password change).
     */
    @Transactional
    public void revokeAllForUser(String userId, String reason) {
        List<AdminSession> active = sessionRepository.findByAdminUserIdAndStatus(userId, "ACTIVE");
        for (AdminSession session : active) {
            revokeSession(session.getSessionId(), reason);
        }
        log.info("Revoked all admin sessions for user: userId={}, count={}, reason={}",
                userId, active.size(), reason);
    }

    /**
     * Get all active sessions for a user.
     */
    public List<AdminSession> getActiveSessions(String userId) {
        return sessionRepository.findByAdminUserIdAndStatus(userId, "ACTIVE");
    }

    private void cacheSession(AdminSession session) {
        String key = SESSION_CACHE_PREFIX + session.getSessionId();
        redisTemplate.opsForValue().set(key, session.getAdminUserId(),
                Duration.ofSeconds(inactivityTimeoutSeconds));
    }
}
