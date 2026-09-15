package com.selfcare.admin.web;

import com.selfcare.admin.security.AdminJwtIssuer;
import com.selfcare.admin.service.MfaService;
import com.selfcare.admin.service.MfaService.BackupCode;
import com.selfcare.admin.service.MfaService.SetupCompleteResult;
import com.selfcare.admin.service.MfaService.SetupResult;
import com.selfcare.platform.common.security.JwtService;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.platform.common.web.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MFA TOTP controller for selfcare Studio admins.
 *
 * Endpoints:
 * <pre>
 * POST /api/v1/admin/mfa/setup         — begin setup, returns QR code + provisioning URI
 * POST /api/v1/admin/mfa/verify-setup  — verify first code, enable MFA, return backup codes
 * POST /api/v1/admin/mfa/verify        — verify TOTP code at login
 * POST /api/v1/admin/mfa/disable       — disable MFA (requires current TOTP code)
 * GET  /api/v1/admin/mfa/status        — get MFA status for the current user
 * </pre>
 *
 * All endpoints except /verify require a valid admin access token.
 * The /verify endpoint accepts either a Bearer token (admin context) or
 * an unauthenticated request with userId + code (for the login flow).
 *
 * @see MfaService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final AdminJwtIssuer jwtIssuer;

    // -------------------------------------------------------------------------
    // Setup (authenticated)
    // -------------------------------------------------------------------------

    /**
     * Begin MFA setup. Returns a QR code and provisioning URI.
     *
     * Scan the QR code with Google Authenticator, Authy, or any TOTP app.
     * After scanning, call {@link #verifySetup} to complete activation.
     */
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<SetupResponse>>
    beginSetup(@RequestHeader("Authorization") String authorization) {
        String userId = extractUserId(authorization);
        SetupResult result = mfaService.beginSetup(userId);
        log.info("MFA setup initiated: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.of(new SetupResponse(
                result.provisioningUri(),
                "data:image/png;base64," + result.qrCodeImage(),
                result.setupToken()
        )));
    }

    /**
     * Verify the first TOTP code from the authenticator app to complete setup.
     * On success, MFA is enabled and 10 backup codes are returned (show once only).
     */
    @PostMapping("/verify-setup")
    public ResponseEntity<ApiResponse<VerifySetupResponse>>
    verifySetup(@RequestHeader("Authorization") String authorization,
                @RequestBody VerifySetupRequest request) {
        String userId = extractUserId(authorization);
        SetupCompleteResult result = mfaService.completeSetup(userId, request.totpCode());
        List<String> codes = result.backupCodes().stream()
                .map(BackupCode::code)
                .toList();
        log.info("MFA setup completed and enabled: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.of(new VerifySetupResponse(
                true,
                codes
        )));
    }

    // -------------------------------------------------------------------------
    // Login-step verification (unauthenticated — login flow only)
    // -------------------------------------------------------------------------

    /**
     * Verify a TOTP code at login time.
     *
     * This endpoint is intentionally unauthenticated — the admin portal calls it
     * after a successful password login when MFA is required, passing the
     * pending session ID and the TOTP code.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyResponse>>
    verifyLoginCode(@RequestBody LoginMfaRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            throw new UnauthorizedException("userId is required");
        }
        boolean valid = mfaService.verifyLoginCode(request.userId(), request.totpCode());
        if (!valid) {
            throw new UnauthorizedException("Invalid verification code");
        }
        log.info("MFA login verification succeeded: userId={}", request.userId());
        return ResponseEntity.ok(ApiResponse.of(new VerifyResponse(true)));
    }

    // -------------------------------------------------------------------------
    // Status and disable (authenticated)
    // -------------------------------------------------------------------------

    /**
     * Get the current MFA status for the authenticated admin.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MfaService.MfaStatus>>
    getStatus(@RequestHeader("Authorization") String authorization) {
        String userId = extractUserId(authorization);
        return ResponseEntity.ok(ApiResponse.of(mfaService.getStatus(userId)));
    }

    /**
     * Disable MFA for the authenticated admin.
     * Requires the current TOTP code as confirmation.
     */
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>>
    disable(@RequestHeader("Authorization") String authorization,
            @RequestBody DisableMfaRequest request) {
        String userId = extractUserId(authorization);
        mfaService.disable(userId, request.totpCode());
        log.info("MFA disabled: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing or invalid Authorization header");
        }
        String token = authorization.substring(7);
        try {
            JwtService.JwtClaims claims = jwtIssuer.validate(token);
            return claims.getSubject();
        } catch (Exception e) {
            throw new UnauthorizedException("Invalid token: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // DTOs / records
    // -------------------------------------------------------------------------

    public record SetupResponse(
            String provisioningUri,
            String qrCodeImage, // base64 PNG with data URI prefix
            String setupToken
    ) {}

    public record VerifySetupResponse(
            boolean success,
            List<String> backupCodes // plain text — shown only now
    ) {}

    public record VerifySetupRequest(
            String totpCode
    ) {}

    public record LoginMfaRequest(
            String userId,
            String totpCode
    ) {}

    public record VerifyResponse(
            boolean success
    ) {}

    public record DisableMfaRequest(
            String totpCode
    ) {}
}
