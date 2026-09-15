package com.selfcare.account.web;

import com.selfcare.account.domain.Account;
import com.selfcare.account.domain.Connection;
import com.selfcare.account.service.AccountService;
import com.selfcare.account.service.ConnectionService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.platform.common.web.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Me" profile REST API — the authenticated user's own profile.
 *
 * Mapped by the api-gateway from /api/v1/me/** to this service so mobile
 * profile screens are auth-context-only (no path-derived identities).
 *
 * Endpoints:
 *   GET /api/v1/me          — profile summary of the current user
 *   GET /api/v1/me/connections — linked connections of the current user
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Current authenticated user profile and connections")
public class MeController {

    private final AccountService accountService;
    private final ConnectionService connectionService;

    @GetMapping
    @Operation(summary = "Get current user profile summary")
    public ResponseEntity<ApiResponse<MeProfile>> getProfile() {
        Account account = currentAccount();
        Connection primary = null;
        try {
            primary = connectionService.getByNumber(account.getPrimaryIdentity());
        } catch (NotFoundException ignored) {
            // Primary connection may not be provisioned yet
        }
        return ResponseEntity.ok(ApiResponse.of(
                new MeProfile(account, primary, connectionService.countLinked(account.getAccountId())),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/connections")
    @Operation(summary = "Get current user's linked connections")
    public ResponseEntity<ApiResponse<List<Connection>>> getConnections() {
        Account account = currentAccount();
        return ResponseEntity.ok(ApiResponse.of(
                connectionService.listConnections(account.getAccountId()),
                TenantContext.get().getCorrelationId()));
    }

    private Account currentAccount() {
        String userId = TenantContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("No authenticated user");
        }
        String primaryIdentity = userId.startsWith("MSISDN:") ? userId.substring("MSISDN:".length()) : userId;
        try {
            return accountService.getByPrimaryIdentity(primaryIdentity);
        } catch (NotFoundException ex) {
            log.info("No account found for primaryIdentity={}, auto-creating", primaryIdentity);
            return accountService.getOrCreate(primaryIdentity);
        }
    }

    /** Profile summary record returned by /api/v1/me. */
    public record MeProfile(
            String accountId,
            String primaryIdentity,
            String displayName,
            String email,
            String status,
            Connection primaryConnection,
            long linkedConnections
    ) {
        MeProfile(Account account, Connection primary, long linkedConnections) {
            this(
                    account.getAccountId(),
                    account.getPrimaryIdentity(),
                    account.getDisplayName(),
                    account.getEmail(),
                    account.getStatus(),
                    primary,
                    linkedConnections
            );
        }
    }
}