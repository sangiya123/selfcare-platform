package com.selfcare.identity.web;

import com.selfcare.identity.service.CidAuthService;
import com.selfcare.platform.common.adapter.CidAuthProvider;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CID login controller — authorization-code (MIFE redirect) authentication.
 *
 * <p>The client redirects through the operator's SSO and returns with an
 * authorization code; this endpoint exchanges it for a platform session and
 * token pair whose profile fields mirror the legacy {@code JwtResponse} shape
 * (subs, username, cxName, profile id/version, wallet id, connection list,
 * header-enrichment).</p>
 *
 * <p>Routed through the API gateway at /api/v1/auth/**. Must be permitted
 * (unauthenticated) in CustomerIdentitySecurityConfig.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/cid")
@RequiredArgsConstructor
@Tag(name = "CID Authentication",
        description = "Authorization-code (MIFE redirect) customer login")
public class CidAuthController {

    private final CidAuthService cidAuthService;

    /**
     * Exchange a CID authorization code for a session + token pair.
     *
     * @param request the exchange request (code is required)
     * @param http    HTTP request (for IP extraction)
     * @return 200 OK with the token pair and the canonical identity profile
     */
    @PostMapping(value = "/exchange", produces = "application/json", consumes = "application/json")
    @Operation(summary = "Exchange a CID authorization code",
            description = "Exchanges the operator authorization code for a platform " +
                          "session and JWT pair, returning the customer profile.")
    public ResponseEntity<ApiResponse<CidExchangeResponse>> exchange(
            @Valid @RequestBody CidExchangeRequest request,
            HttpServletRequest http) {
        log.info("CID exchange request: redirectUri={}, deviceId={}",
                request.redirectUri(), request.deviceId());

        CidAuthService.CidLoginResult result = cidAuthService.exchange(
                request.code(),
                request.redirectUri(),
                request.deviceId(),
                request.deviceDescription(),
                clientIp(http));

        return ResponseEntity.ok(ApiResponse.of(CidExchangeResponse.from(result)));
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // -------------------------------------------------------------------------
    // Request / response records
    // -------------------------------------------------------------------------

    /** Request body for the CID exchange. */
    public record CidExchangeRequest(
            @NotBlank String code,
            String redirectUri,
            String deviceId,
            String deviceDescription
    ) {}

    /**
     * Response body for the CID exchange — token pair plus the legacy-shaped
     * identity profile.
     */
    public record CidExchangeResponse(
            String accessToken,
            String refreshToken,
            String sessionId,
            String subs,
            String username,
            String cxName,
            boolean headerEnriched,
            String profileId,
            String profileVersion,
            String profileImageUrl,
            String walletId,
            List<CidAuthProvider.ConnectionDetail> connectionList,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn
    ) {
        static CidExchangeResponse from(CidAuthService.CidLoginResult result) {
            CidAuthProvider.CidExchangeResult identity = result.identity();
            return new CidExchangeResponse(
                    result.tokens().accessToken(),
                    result.tokens().refreshToken(),
                    result.sessionId(),
                    identity.cidUuid(),
                    identity.username(),
                    identity.cxName(),
                    identity.headerEnriched(),
                    identity.profileId(),
                    identity.profileVersion(),
                    identity.profileImageUrl(),
                    identity.walletId(),
                    identity.connections(),
                    "Bearer",
                    result.tokens().expiresInSeconds(),
                    result.tokens().refreshExpiresInSeconds()
            );
        }
    }
}