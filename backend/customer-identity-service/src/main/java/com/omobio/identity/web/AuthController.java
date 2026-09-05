package com.omobio.identity.web;

import com.omobio.identity.domain.Session;
import com.omobio.identity.security.JwtIssuer;
import com.omobio.identity.service.CustomerSessionService;
import com.omobio.identity.service.OtpService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Authentication REST controller — public customer login endpoints.
 *
 * <p>All paths are routed through the API gateway at /api/v1/auth/**.
 * The gateway strips no prefix, so the controller paths are the canonical
 * API surface.
 *
 * <p>Endpoints (ADR-011 token policy in effect):
 * <ul>
 *   <li>POST   /api/v1/auth/otp              — request an OTP
 *   <li>POST   /api/v1/auth/otp/verify       — verify OTP, mint tokens
 *   <li>POST   /api/v1/auth/refresh          — rotate refresh token
 *   <li>POST   /api/v1/auth/signout          — sign out current session
 *   <li>GET    /api/v1/auth/sessions         — list active sessions
 *   <li>DELETE /api/v1/auth/sessions/{id}    — remote-revoke a session
 * </ul>
 *
 * <p>Refresh token rotation follows CustomerSessionService.rotateRefreshToken:
 * the previous token is replayed; if it matches the stored previous hash,
 * the entire token family is revoked (replay-detection).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Public customer authentication endpoints")
public class AuthController {

    private final OtpService otpService;
    private final CustomerSessionService sessionService;
    private final JwtIssuer jwtIssuer;

    // -------------------------------------------------------------------------
    // OTP
    // -------------------------------------------------------------------------

    /**
     * Request an OTP to be sent to the given identifier.
     *
     * @param request  the OTP generate request body
     * @param request2  HTTP request (for IP extraction)
     * @return 202 Accepted with correlationId and expiresIn
     */
    @PostMapping(value = "/otp", produces = "application/json", consumes = "application/json")
    @Operation(summary = "Request an OTP",
            description = "Generates and dispatches an OTP to the given identifier via the chosen channel.")
    public ResponseEntity<ApiResponse<OtpGenerateResponse>> requestOtp(
            @Valid @RequestBody OtpGenerateRequest request,
            HttpServletRequest request2) {
        log.info("OTP generate request: identifier={}, channel={}",
                maskIdentifier(request.identifier()), request.channel());

        OtpService.OtpGenerateResult result = otpService.generateOtp(
                null, request.identifier(), request.channel());

        OtpGenerateResponse body = new OtpGenerateResponse(
                result.correlationId(),
                result.expiresAt(),
                result.expiresInSeconds()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(body));
    }

    /**
     * Verify an OTP and mint a session + tokens.
     *
     * @param request the verify request body
     * @return 200 OK with access/refresh tokens and the new sessionId
     */
    @PostMapping(value = "/otp/verify", produces = "application/json", consumes = "application/json")
    @Operation(summary = "Verify an OTP",
            description = "Verifies the OTP code and, on success, issues access + refresh tokens.")
    public ResponseEntity<ApiResponse<TokenResponse>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest) {
        log.info("OTP verify request: identifier={}, correlationId={}",
                maskIdentifier(request.identifier()), request.correlationId());

        var otp = otpService.verifyOtp(
                null, request.identifier(), request.code(), request.correlationId());

        String tenantId = TenantContext.get().getTenantId();
        String userId = extractUserIdFromOtp(otp);

        // Create a durable session record first (this is what the JWT will reference)
        Session session = sessionService.createSession(
                userId,
                request.deviceId(),
                request.deviceDescription(),
                clientIp(httpRequest)
        );

        // Issue the access + refresh token pair bound to the new session
        JwtIssuer.IssuedTokens tokens = jwtIssuer.issueForSession(session);

        log.info("Login successful: tenant={}, userId={}, sessionId={}",
                tenantId, userId, session.getSessionId());

        TokenResponse body = new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                session.getSessionId(),
                tokens.expiresInSeconds(),
                tokens.refreshExpiresInSeconds(),
                "Bearer"
        );

        return ResponseEntity.ok(ApiResponse.of(body));
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    /**
     * Refresh access + refresh token pair.
     *
     * <p>The refresh token is rotated; the previous token is invalidated.
     * If the presented refresh token is the previously-used one (replay),
     * the entire token family is revoked (per ADR-011).
     *
     * @param request the refresh request
     * @return 200 OK with new token pair
     */
    @PostMapping(value = "/refresh", produces = "application/json", consumes = "application/json")
    @Operation(summary = "Refresh tokens",
            description = "Rotates the refresh token and returns a new access/refresh pair. " +
                          "Replays of the previous refresh token trigger family-level revocation.")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        log.info("Refresh request");

        // Validate the refresh token (signature, expiry, type=refresh)
        var claims = jwtIssuer.validateRefreshToken(request.refreshToken());

        // Mint the new tokens FIRST so we can hand the new refresh to the rotation logic
        String sessionId = claims.getSessionId();

        // Find the previous refresh token (the one being rotated). We rely on the
        // controller's caller to have provided it for rotation detection; otherwise
        // we issue a new pair and let the session service's rotation reject replays
        // via the prior hash check.
        Session session = sessionService.getActiveSessions(claims.getSubject()).stream()
                .filter(s -> sessionId != null && sessionId.equals(s.getSessionId()))
                .findFirst()
                .orElseThrow(() -> new UnauthorizedException("Session not found or expired"));

        // Generate a new refresh token (its hash will be stored as the new current)
        JwtIssuer.IssuedTokens newTokens = jwtIssuer.issueForSession(session);

        // Rotate — passing the OLD refresh token as the previous argument.
        // The session service detects replay if the previous hash matches stored previous.
        try {
            session = sessionService.rotateRefreshToken(
                    sessionId,
                    newTokens.refreshToken(),
                    request.refreshToken()
            );
        } catch (UnauthorizedException ex) {
            // Family was revoked (replay detected) — surface to caller
            log.warn("Refresh rejected: {}", ex.getMessage());
            throw ex;
        }

        TokenResponse body = new TokenResponse(
                newTokens.accessToken(),
                newTokens.refreshToken(),
                session.getSessionId(),
                newTokens.expiresInSeconds(),
                newTokens.refreshExpiresInSeconds(),
                "Bearer"
        );

        return ResponseEntity.ok(ApiResponse.of(body));
    }

    // -------------------------------------------------------------------------
    // Signout
    // -------------------------------------------------------------------------

    /**
     * Sign out the current session.
     *
     * <p>The session ID is read from the JWT claim (set by JwtAuthenticationConverter
     * via TenantContext). All tokens in the session's family are not affected —
     * the caller may have multiple devices and only this one is logged out.
     *
     * @return 204 No Content
     */
    @PostMapping(value = "/signout", produces = "application/json")
    @Operation(summary = "Sign out current session",
            description = "Revokes the session identified by the JWT. The token family is NOT revoked.")
    public ResponseEntity<Void> signout() {
        String sessionId = TenantContext.get().getSessionId();
        String userId = TenantContext.get().getUserId();
        if (sessionId == null) {
            throw new UnauthorizedException("No active session");
        }
        sessionService.revokeSession(sessionId, "USER_SIGNOUT");
        log.info("User signed out: userId={}, sessionId={}", userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Sessions
    // -------------------------------------------------------------------------

    /**
     * List active sessions for the current user.
     *
     * @return 200 with list of session views
     */
    @GetMapping(value = "/sessions", produces = "application/json")
    @Operation(summary = "List active sessions",
            description = "Returns all ACTIVE sessions for the authenticated user.")
    public ResponseEntity<ApiResponse<List<SessionView>>> listSessions() {
        String userId = TenantContext.get().getUserId();
        if (userId == null) {
            throw new UnauthorizedException("No authenticated user");
        }
        List<Session> sessions = sessionService.getActiveSessions(userId);
        List<SessionView> views = sessions.stream()
                .map(SessionView::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.of(views));
    }

    /**
     * Remotely revoke a session.
     *
     * @param sessionId the session to revoke
     * @return 204 No Content
     */
    @DeleteMapping(value = "/sessions/{sessionId}", produces = "application/json")
    @Operation(summary = "Remote-revoke a session",
            description = "Revokes the specified session belonging to the current user.")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        String userId = TenantContext.get().getUserId();
        String tenantId = TenantContext.get().getTenantId();

        // Verify ownership (tenant + user)
        Optional<Session> sessionOpt = sessionRepository().findByTenantIdAndSessionId(tenantId, sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Session session = sessionOpt.get();
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new UnauthorizedException("Session does not belong to current user");
        }

        sessionService.revokeSession(sessionId, "USER_REMOTE_REVOKE");
        log.info("Session remote-revoked: sessionId={}, userId={}", sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private com.omobio.identity.repository.SessionRepository sessionRepository() {
        // Late resolve via the session service to avoid a circular bean.
        return sessionService.getSessionRepository();
    }

    /**
     * Best-effort user ID extraction from an OTP.
     *
     * <p>Telco: the identifier is an MSISDN. The userId is the customer's NIC
     * hash or canonical ID. Since this identity service does not own the
     * customer registry, we return a stable per-tenant synthetic ID derived
     * from the MSISDN. In production this is replaced by a call to the
     * account-entitlement-service to resolve the primary connection.
     */
    private String extractUserIdFromOtp(com.omobio.identity.domain.OtpCode otp) {
        return "MSISDN:" + otp.getIdentifier();
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() <= 6) return "***";
        return identifier.substring(0, 3) + "***" + identifier.substring(identifier.length() - 3);
    }

    // -------------------------------------------------------------------------
    // Request / response records
    // -------------------------------------------------------------------------

    /** Request body for OTP generation. */
    public record OtpGenerateRequest(
            @NotBlank String identifier,
            @NotBlank String channel
    ) {}

    /** Response body for OTP generation. */
    public record OtpGenerateResponse(
            String correlationId,
            java.time.Instant expiresAt,
            int expiresIn
    ) {}

    /** Request body for OTP verification. */
    public record OtpVerifyRequest(
            String identifier,
            @NotBlank String code,
            String correlationId,
            String deviceId,
            String deviceDescription
    ) {}

    /** Request body for refresh. */
    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    /** Response body for verify + refresh — full token pair. */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String sessionId,
            long expiresIn,
            long refreshExpiresIn,
            String tokenType
    ) {}

    /** View of a session for the /sessions endpoint. */
    public record SessionView(
            String sessionId,
            String deviceId,
            String deviceDescription,
            String ipAddress,
            String status,
            java.time.Instant issuedAt,
            java.time.Instant lastUsedAt,
            java.time.Instant expiresAt
    ) {
        static SessionView from(Session s) {
            return new SessionView(
                    s.getSessionId(),
                    s.getDeviceId(),
                    s.getDeviceDescription(),
                    s.getIpAddress(),
                    s.getStatus(),
                    s.getIssuedAt(),
                    s.getLastUsedAt(),
                    s.getExpiresAt()
            );
        }
    }
}
