package com.omobio.admin.service;

import com.omobio.admin.domain.AdminSession;
import com.omobio.admin.domain.AdminUser;
import com.omobio.admin.repository.AdminSessionRepository;
import com.omobio.admin.repository.AdminUserRepository;
import com.omobio.admin.security.AdminJwtIssuer;
import com.omobio.admin.security.AdminPasswordEncoder;
import com.omobio.platform.common.security.JwtService;
import com.omobio.platform.common.web.BadRequestException;
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

/**
 * Admin authentication service.
 *
 * Provides login, refresh, logout, and change password operations.
 * Mirrors the pattern of {@code CustomerSessionService} but for admin users.
 *
 * @see AdminSessionService
 * @see com.omobio.admin.security.AdminJwtIssuer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserRepository userRepository;
    private final AdminSessionRepository sessionRepository;
    private final AdminPasswordEncoder passwordEncoder;
    private final AdminJwtIssuer jwtIssuer;
    private final AdminSessionService sessionService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${omobio.security.session.refresh-ttl-seconds:2592000}")
    private long refreshTtlSeconds;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final String SESSION_CACHE_PREFIX = "omobio:admin:session:";

    /**
     * Login with email and password.
     *
     * @param email     admin user email
     * @param password  plain-text password
     * @param deviceId  optional device identifier
     * @param ipAddress client IP
     * @return login result with access token, refresh token, and user info
     * @throws UnauthorizedException if credentials are invalid
     */
    @Transactional
    public LoginResult login(String email, String password, String deviceId, String ipAddress) {
        AdminUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("Admin login failed (unknown user): email={}", email);
            throw new UnauthorizedException("Invalid credentials");
        }

        // Check lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            log.warn("Admin login attempted on locked account: email={}", email);
            throw new UnauthorizedException("Account is temporarily locked");
        }

        // Check status
        if (!AdminUser.Status.ACTIVE.name().equals(user.getStatus())) {
            log.warn("Admin login attempted on non-active account: email={}, status={}",
                    email, user.getStatus());
            throw new UnauthorizedException("Account is not active");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            user.setLastFailedLoginAt(Instant.now());
            if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                user.setStatus(AdminUser.Status.LOCKED.name());
                log.warn("Admin account locked due to too many failed attempts: email={}", email);
            }
            userRepository.save(user);
            throw new UnauthorizedException("Invalid credentials");
        }

        // Reset failed count and update last login
        user.setFailedLoginCount(0);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(ipAddress);
        user.setLockedUntil(null);
        if (AdminUser.Status.LOCKED.name().equals(user.getStatus())) {
            user.setStatus(AdminUser.Status.ACTIVE.name());
        }
        userRepository.save(user);

        // If MFA is enabled, do not issue tokens yet — the caller must call
        // /mfa/verify with the TOTP code to complete the login.
        if (user.isMfaEnabled() && "TOTP".equals(user.getMfaType())) {
            log.info("Admin login pending MFA: userId={}", user.getId());
            return LoginResult.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .tenantId(user.getTenantId())
                    .role(user.getRole())
                    .mfaPending(true)
                    .build();
        }

        // Create session
        AdminSession session = sessionService.createSession(
                user, deviceId, ipAddress, null);

        // Issue tokens
        String accessToken = jwtIssuer.issueAccessToken(
                user.getId(), user.getTenantId(), session.getSessionId(),
                user.getRole(), "production");
        String refreshToken = jwtIssuer.issueRefreshToken(
                user.getId(), user.getTenantId(), session.getSessionId());

        // Update session with token hashes
        session.setCurrentTokenHash(hashToken(refreshToken));
        session.setPreviousTokenHash(null);
        sessionRepository.save(session);

        log.info("Admin login successful: userId={}, tenantId={}, role={}",
                user.getId(), user.getTenantId(), user.getRole());

        return LoginResult.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .sessionId(session.getSessionId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .mfaPending(false)
                .build();
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * @param refreshToken     the current refresh token
     * @return a new access token and rotated refresh token
     */
    @Transactional
    public LoginResult refresh(String refreshToken) {
        JwtService.JwtClaims claims;
        try {
            claims = jwtIssuer.validate(refreshToken);
        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (!"refresh".equals(claims.getTokenType())) {
            throw new UnauthorizedException("Token is not a refresh token");
        }

        AdminSession session = sessionRepository.findById(claims.getSessionId())
                .orElseThrow(() -> new UnauthorizedException("Session not found"));
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new UnauthorizedException("Session is not active");
        }

        AdminUser user = userRepository.findById(session.getAdminUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Replay detection: if provided refresh token matches "previous", revoke
        String tokenHash = hashToken(refreshToken);
        if (tokenHash.equals(session.getPreviousTokenHash())) {
            log.warn("Refresh token replay detected: sessionId={}", session.getSessionId());
            sessionService.revokeTokenFamily(session.getTokenFamilyId(), "REPLAY_DETECTED");
            throw new UnauthorizedException("Refresh token replay detected");
        }
        if (!tokenHash.equals(session.getCurrentTokenHash())) {
            throw new UnauthorizedException("Refresh token mismatch");
        }

        // Rotate
        String newRefreshToken = jwtIssuer.issueRefreshToken(
                user.getId(), user.getTenantId(), session.getSessionId());
        session.setPreviousTokenHash(session.getCurrentTokenHash());
        session.setCurrentTokenHash(hashToken(newRefreshToken));
        session.setLastUsedAt(Instant.now());
        session.setExpiresAt(Instant.now().plusSeconds(refreshTtlSeconds));
        sessionRepository.save(session);

        String newAccessToken = jwtIssuer.issueAccessToken(
                user.getId(), user.getTenantId(), session.getSessionId(),
                user.getRole(), claims.getEnvironment());

        return LoginResult.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .sessionId(session.getSessionId())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    /**
     * Logout (revoke a session).
     *
     * @param sessionId the session ID
     * @param reason   reason for logout
     */
    @Transactional
    public void logout(String sessionId, String reason) {
        sessionService.revokeSession(sessionId, reason != null ? reason : "USER_LOGOUT");
    }

    /**
     * Change a user's password.
     *
     * @param userId      the user changing the password
     * @param oldPassword current password (for verification)
     * @param newPassword the new password
     */
    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        AdminUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("New password must be at least 8 characters");
        }
        user.setPasswordHash(passwordEncoder.hash(newPassword));
        userRepository.save(user);
        log.info("Admin password changed: userId={}", userId);

        // Revoke all existing sessions to force re-authentication
        sessionService.revokeAllForUser(userId, "PASSWORD_CHANGED");
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

    // -------------------------------------------------------------------------
    // DTO
    // -------------------------------------------------------------------------

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LoginResult {
        private String userId;
        private String email;
        private String fullName;
        private String tenantId;
        private String role;
        private String sessionId;
        private String accessToken;
        private String refreshToken;
        /**
         * If true, the caller must complete MFA verification via
         * {@code /api/v1/admin/mfa/verify} before any token is issued.
         * Tokens in this result will be null.
         */
        private boolean mfaPending;
    }
}
