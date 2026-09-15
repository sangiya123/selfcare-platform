package com.selfcare.admin.web;

import com.selfcare.admin.domain.AdminSession;
import com.selfcare.admin.domain.AdminUser;
import com.selfcare.admin.repository.AdminUserRepository;
import com.selfcare.admin.security.AdminJwtIssuer;
import com.selfcare.admin.service.AdminAuthService;
import com.selfcare.admin.service.AdminSessionService;
import com.selfcare.platform.common.security.JwtService;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.platform.common.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin authentication controller.
 *
 * Handles login, refresh, logout, and "me" (current user) endpoints.
 *
 * @see AdminAuthService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService authService;
    private final AdminSessionService sessionService;
    private final AdminUserRepository userRepository;
    private final AdminJwtIssuer jwtIssuer;

    /**
     * Login with email and password.
     *
     * @param email    admin email
     * @param password plain-text password
     * @param deviceId optional device ID
     * @return access token, refresh token, and user info
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminAuthService.LoginResult>>
    login(@RequestParam String email,
          @RequestParam String password,
          @RequestParam(required = false) String deviceId,
          HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        log.info("Admin login attempt: email={}", email);
        AdminAuthService.LoginResult result = authService.login(email, password, deviceId, ipAddress);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    /**
     * Refresh an access token.
     *
     * @param refreshToken the refresh token from login
     * @return new access token and refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AdminAuthService.LoginResult>>
    refresh(@RequestParam String refreshToken) {
        log.debug("Admin token refresh");
        AdminAuthService.LoginResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    /**
     * Logout the current session.
     *
     * @param sessionId session ID from the access token
     * @param reason    optional logout reason
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>>
    logout(@RequestParam String sessionId,
           @RequestParam(required = false) String reason) {
        log.info("Admin logout: sessionId={}", sessionId);
        authService.logout(sessionId, reason);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    /**
     * Get the current authenticated admin's profile.
     *
     * @param authorization Bearer token
     * @return user profile
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>>
    me(@RequestHeader("Authorization") String authorization) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        AdminUser user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return ResponseEntity.ok(ApiResponse.of(MeResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .tenantId(user.getTenantId())
                .role(user.getRole())
                .mfaEnabled(user.isMfaEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .build()));
    }

    /**
     * Change own password.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>>
    changePassword(@RequestHeader("Authorization") String authorization,
                   @RequestBody ChangePasswordRequest request) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        authService.changePassword(claims.getSubject(), request.oldPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    /**
     * List all active sessions for the current admin.
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<AdminSession>>>
    listSessions(@RequestHeader("Authorization") String authorization) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        List<AdminSession> sessions = sessionService.getActiveSessions(claims.getSubject());
        return ResponseEntity.ok(ApiResponse.of(sessions));
    }

    /**
     * Revoke a specific session.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>>
    revokeSession(@RequestHeader("Authorization") String authorization,
                  @PathVariable String sessionId) {
        JwtService.JwtClaims claims = extractClaims(authorization);
        sessionService.revokeSession(sessionId, "USER_REVOKED:" + claims.getSubject());
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    private JwtService.JwtClaims extractClaims(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        String token = authorization.substring(7);
        try {
            return jwtIssuer.validate(token);
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid token: " + e.getMessage());
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return xForwardedFor != null ? xForwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MeResponse {
        private String userId;
        private String email;
        private String fullName;
        private String tenantId;
        private String role;
        private boolean mfaEnabled;
        private java.time.Instant lastLoginAt;
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
}
