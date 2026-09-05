package com.omobio.admin.web;

import com.omobio.admin.domain.AuthSettings;
import com.omobio.admin.repository.AuthSettingsRepository;
import com.omobio.admin.security.AdminJwtIssuer;
import com.omobio.admin.service.RbacService;
import com.omobio.platform.common.security.JwtService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.BadRequestException;
import com.omobio.platform.common.web.ForbiddenException;
import com.omobio.platform.common.web.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Auth & Security settings controller.
 *
 * Endpoints (all under {@code /api/v1/admin/settings/auth}):
 *
 *   GET    /                          Get current auth settings
 *   PUT    /                          Update all auth settings
 *   GET    /otp-policy                Get OTP policy (length, TTL, attempts, lockout)
 *   PUT    /otp-policy                Update OTP policy
 *   GET    /oidc                      Get SSO/OIDC configuration
 *   PUT    /oidc                      Update SSO/OIDC configuration
 *   GET    /session-policy            Get session & device policy
 *   PUT    /session-policy            Update session policy
 *   GET    /risk-rules                Get risk rules
 *   PUT    /risk-rules                Update risk rules
 *   GET    /allowed-origins           Get CORS allowed origins
 *   PUT    /allowed-origins           Update allowed origins
 *
 * Settings are tenant-scoped (X-Tenant-Id) and read/write requires
 * the Security Admin role.
 *
 * Sensitive fields (client secrets, OIDC token signing keys) are
 * referenced as secret-manager pointers, never stored in plaintext
 * outside of encrypted storage.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/settings/auth")
@RequiredArgsConstructor
@Tag(name = "Auth & Security Settings", description = "Auth policy, OTP, SSO, sessions, risk rules, CORS")
public class AuthSettingsController {

    private final AuthSettingsRepository repository;
    private final RbacService rbacService;
    private final AdminJwtIssuer jwtIssuer;

    // ---------------------------------------------------------------------
    // Whole-document GET/PUT
    // ---------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Get full auth & security settings for the current tenant")
    public ResponseEntity<ApiResponse<AuthSettings>> get() {
        requireSecurityAdmin();
        String tenantId = TenantContext.get().getTenantId();
        AuthSettings settings = repository.findByTenantId(tenantId)
                .orElseGet(() -> defaultSettings(tenantId));
        // Never echo client secrets in clear
        if (settings.getOidc() != null) {
            settings.getOidc().setClientSecret(null);
        }
        return ResponseEntity.ok(ApiResponse.of(settings));
    }

    @PutMapping
    @Operation(summary = "Update full auth & security settings")
    public ResponseEntity<ApiResponse<AuthSettings>> update(@RequestBody AuthSettings input) {
        requireSecurityAdmin();
        String tenantId = TenantContext.get().getTenantId();
        input.setTenantId(tenantId);
        AuthSettings saved = repository.save(input);
        log.info("Auth settings updated by {} for tenant {}",
                TenantContext.get().getUserId(), tenantId);
        return ResponseEntity.ok(ApiResponse.of(saved));
    }

    // ---------------------------------------------------------------------
    // Granular sub-resources
    // ---------------------------------------------------------------------

    @GetMapping("/otp-policy")
    @Operation(summary = "Get OTP policy")
    public ResponseEntity<ApiResponse<AuthSettings.OtpPolicy>> getOtpPolicy() {
        requireSecurityAdmin();
        AuthSettings settings = loadOrDefault();
        return ResponseEntity.ok(ApiResponse.of(
                settings.getOtpPolicy() != null ? settings.getOtpPolicy() : defaultOtpPolicy()));
    }

    @PutMapping("/otp-policy")
    @Operation(summary = "Update OTP policy")
    public ResponseEntity<ApiResponse<AuthSettings.OtpPolicy>> updateOtpPolicy(
            @RequestBody AuthSettings.OtpPolicy policy) {
        requireSecurityAdmin();
        validateOtpPolicy(policy);
        AuthSettings settings = loadOrDefault();
        settings.setOtpPolicy(policy);
        repository.save(settings);
        log.info("OTP policy updated: length={}, expiry={}s, maxAttempts={}",
                policy.getCodeLength(), policy.getExpirySeconds(), policy.getMaxAttempts());
        return ResponseEntity.ok(ApiResponse.of(policy));
    }

    @GetMapping("/oidc")
    @Operation(summary = "Get SSO/OIDC configuration")
    public ResponseEntity<ApiResponse<AuthSettings.OidcConfig>> getOidc() {
        requireSecurityAdmin();
        AuthSettings settings = loadOrDefault();
        AuthSettings.OidcConfig oidc = settings.getOidc() != null
                ? settings.getOidc() : new AuthSettings.OidcConfig();
        oidc.setClientSecret(null);  // never echo
        return ResponseEntity.ok(ApiResponse.of(oidc));
    }

    @PutMapping("/oidc")
    @Operation(summary = "Update SSO/OIDC configuration")
    public ResponseEntity<ApiResponse<AuthSettings.OidcConfig>> updateOidc(
            @RequestBody AuthSettings.OidcConfig oidc) {
        requireSecurityAdmin();
        if (oidc.getDiscoveryUrl() != null && !oidc.getDiscoveryUrl().startsWith("https://")) {
            throw new BadRequestException("OIDC discoveryUrl must use HTTPS");
        }
        AuthSettings settings = loadOrDefault();
        settings.setOidc(oidc);
        repository.save(settings);
        log.info("OIDC config updated: provider={}, discoveryUrl={}",
                oidc.getProviderType(), oidc.getDiscoveryUrl());
        return ResponseEntity.ok(ApiResponse.of(oidc));
    }

    @GetMapping("/session-policy")
    @Operation(summary = "Get session & device policy")
    public ResponseEntity<ApiResponse<AuthSettings.SessionPolicy>> getSessionPolicy() {
        requireSecurityAdmin();
        AuthSettings settings = loadOrDefault();
        return ResponseEntity.ok(ApiResponse.of(
                settings.getSessionPolicy() != null
                        ? settings.getSessionPolicy() : defaultSessionPolicy()));
    }

    @PutMapping("/session-policy")
    @Operation(summary = "Update session policy")
    public ResponseEntity<ApiResponse<AuthSettings.SessionPolicy>> updateSessionPolicy(
            @RequestBody AuthSettings.SessionPolicy policy) {
        requireSecurityAdmin();
        if (policy.getAccessTokenMinutes() < 5 || policy.getAccessTokenMinutes() > 1440) {
            throw new BadRequestException("accessTokenMinutes must be between 5 and 1440");
        }
        if (policy.getRefreshTokenDays() < 1 || policy.getRefreshTokenDays() > 90) {
            throw new BadRequestException("refreshTokenDays must be between 1 and 90");
        }
        AuthSettings settings = loadOrDefault();
        settings.setSessionPolicy(policy);
        repository.save(settings);
        log.info("Session policy updated: accessTtl={}min, refreshTtl={}d, maxSessions={}",
                policy.getAccessTokenMinutes(),
                policy.getRefreshTokenDays(),
                policy.getMaxConcurrentSessions());
        return ResponseEntity.ok(ApiResponse.of(policy));
    }

    @GetMapping("/risk-rules")
    @Operation(summary = "Get risk rules")
    public ResponseEntity<ApiResponse<List<AuthSettings.RiskRule>>> getRiskRules() {
        requireSecurityAdmin();
        AuthSettings settings = loadOrDefault();
        return ResponseEntity.ok(ApiResponse.of(
                settings.getRiskRules() != null ? settings.getRiskRules() : List.of()));
    }

    @PutMapping("/risk-rules")
    @Operation(summary = "Update risk rules")
    public ResponseEntity<ApiResponse<List<AuthSettings.RiskRule>>> updateRiskRules(
            @RequestBody List<AuthSettings.RiskRule> rules) {
        requireSecurityAdmin();
        AuthSettings settings = loadOrDefault();
        settings.setRiskRules(rules);
        repository.save(settings);
        log.info("Risk rules updated: count={}", rules.size());
        return ResponseEntity.ok(ApiResponse.of(rules));
    }

    @GetMapping("/allowed-origins")
    @Operation(summary = "Get CORS allowed origins")
    public ResponseEntity<ApiResponse<List<String>>> getAllowedOrigins() {
        requireSecurityAdmin();
        AuthSettings settings = loadOrDefault();
        return ResponseEntity.ok(ApiResponse.of(
                settings.getAllowedOrigins() != null ? settings.getAllowedOrigins() : List.of()));
    }

    @PutMapping("/allowed-origins")
    @Operation(summary = "Update CORS allowed origins")
    public ResponseEntity<ApiResponse<List<String>>> updateAllowedOrigins(
            @RequestBody List<String> origins) {
        requireSecurityAdmin();
        for (String origin : origins) {
            if (origin != null && !origin.startsWith("https://") && !origin.startsWith("http://localhost")) {
                throw new BadRequestException("Origin must be HTTPS (or localhost for dev): " + origin);
            }
        }
        AuthSettings settings = loadOrDefault();
        settings.setAllowedOrigins(origins);
        repository.save(settings);
        log.info("Allowed origins updated: count={}", origins.size());
        return ResponseEntity.ok(ApiResponse.of(origins));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private AuthSettings loadOrDefault() {
        return repository.findByTenantId(TenantContext.get().getTenantId())
                .orElseGet(() -> defaultSettings(TenantContext.get().getTenantId()));
    }

    private void requireSecurityAdmin() {
        String userId = TenantContext.get().getUserId();
        String token = TenantContext.get().getToken();
        if (userId == null || token == null) {
            throw new UnauthorizedException("Authentication required");
        }
        try {
            JwtService.JwtClaims claims = jwtIssuer.validate(token);
            if (!rbacService.hasPermission(claims.getSubject(), "settings:auth:write")) {
                throw new ForbiddenException("settings:auth:write", "auth-settings");
            }
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid token: " + e.getMessage());
        }
    }

    private void validateOtpPolicy(AuthSettings.OtpPolicy policy) {
        if (policy.getCodeLength() != 6 && policy.getCodeLength() != 8) {
            throw new BadRequestException("OTP codeLength must be 6 or 8");
        }
        if (policy.getExpirySeconds() < 15 || policy.getExpirySeconds() > 600) {
            throw new BadRequestException("OTP expirySeconds must be between 15 and 600");
        }
        if (policy.getMaxAttempts() < 3 || policy.getMaxAttempts() > 10) {
            throw new BadRequestException("OTP maxAttempts must be between 3 and 10");
        }
        if (policy.getLockoutMinutes() < 5 || policy.getLockoutMinutes() > 60) {
            throw new BadRequestException("OTP lockoutMinutes must be between 5 and 60");
        }
    }

    private AuthSettings defaultSettings(String tenantId) {
        AuthSettings s = new AuthSettings();
        s.setTenantId(tenantId);
        s.setOtpPolicy(defaultOtpPolicy());
        s.setSessionPolicy(defaultSessionPolicy());
        return s;
    }

    private AuthSettings.OtpPolicy defaultOtpPolicy() {
        AuthSettings.OtpPolicy p = new AuthSettings.OtpPolicy();
        p.setCodeLength(6);
        p.setAlgorithm("SHA1");
        p.setExpirySeconds(30);
        p.setMaxAttempts(5);
        p.setLockoutMinutes(15);
        p.setBackupCodesPerUser(10);
        p.setAllowQrScan(true);
        return p;
    }

    private AuthSettings.SessionPolicy defaultSessionPolicy() {
        AuthSettings.SessionPolicy p = new AuthSettings.SessionPolicy();
        p.setAccessTokenMinutes(60);
        p.setRefreshTokenDays(30);
        p.setMaxConcurrentSessions(3);
        p.setDeviceFingerprintPolicy("WARN");
        p.setSameDeviceReauth(true);
        return p;
    }
}
