package com.omobio.account.web;

import com.omobio.account.domain.Account;
import com.omobio.account.service.AccountService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.NotFoundException;
import com.omobio.platform.common.web.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Account REST API.
 *
 * Endpoints:
 *   GET    /api/v1/accounts/{id}        — get account by ID
 *   GET    /api/v1/accounts/me          — get current authenticated account
 *   POST   /api/v1/accounts             — create a new account
 *   GET    /api/v1/accounts             — list accounts for current tenant (admin)
 *   PUT    /api/v1/accounts/{id}/status — update account status
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account CRUD and lookup")
public class AccountController {

    private final AccountService accountService;

    /**
     * Get an account by its ID.
     *
     * @param id the account ID
     * @return 200 with the account
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<ApiResponse<Account>> getById(@PathVariable String id) {
        Account account = accountService.getById(id);
        return ResponseEntity.ok(ApiResponse.of(account, TenantContext.get().getCorrelationId()));
    }

    /**
     * Get the current authenticated account.
     * The user ID is read from the JWT via TenantContext.
     *
     * @return 200 with the current account, or 401 if not authenticated
     */
    @GetMapping("/me")
    @Operation(summary = "Get current authenticated account")
    public ResponseEntity<ApiResponse<Account>> getCurrent() {
        String userId = TenantContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("No authenticated user");
        }

        // userId from JWT is typically a synthetic ID like "MSISDN:...".
        // Try to parse the primary identity and look up the account.
        String primaryIdentity = userId.startsWith("MSISDN:") ? userId.substring("MSISDN:".length()) : userId;
        Account account;
        try {
            account = accountService.getByPrimaryIdentity(primaryIdentity);
        } catch (NotFoundException ex) {
            // Account not yet provisioned for this identity
            log.info("No account found for primaryIdentity={}, auto-creating", primaryIdentity);
            account = accountService.getOrCreate(primaryIdentity);
        }
        return ResponseEntity.ok(ApiResponse.of(account, TenantContext.get().getCorrelationId()));
    }

    /**
     * Create a new account.
     *
     * @param request the create-account request
     * @return 201 with the created account
     */
    @PostMapping
    @Operation(summary = "Create a new account")
    public ResponseEntity<ApiResponse<Account>> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(
                request.primaryIdentity(),
                request.displayName(),
                request.email()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(account, TenantContext.get().getCorrelationId()));
    }

    /**
     * List all accounts for the current tenant (admin use).
     *
     * @return 200 with the list of accounts
     */
    @GetMapping
    @Operation(summary = "List accounts for current tenant")
    public ResponseEntity<ApiResponse<List<Account>>> list() {
        List<Account> accounts = accountService.listAccounts();
        return ResponseEntity.ok(ApiResponse.of(accounts, TenantContext.get().getCorrelationId()));
    }

    /**
     * Update account status.
     *
     * @param id      the account ID
     * @param request the status update request
     * @return 200 with no body
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Update account status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {
        accountService.updateStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }

    // ------------------------------------------------------------------
    // Request records
    // ------------------------------------------------------------------

    /** Request body for creating an account. */
    public record CreateAccountRequest(
            @NotBlank String primaryIdentity,
            String displayName,
            String email
    ) {}

    /** Request body for updating account status. */
    public record UpdateStatusRequest(
            @NotBlank String status
    ) {}
}
