package com.omobio.account.web;

import com.omobio.account.domain.Connection;
import com.omobio.account.service.AccountService;
import com.omobio.account.service.ConnectionService;
import com.omobio.account.service.EntitlementService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
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
 * Connection REST API.
 *
 * Endpoints:
 *   GET    /api/v1/accounts/{accountId}/connections                 — list all connections
 *   POST   /api/v1/accounts/{accountId}/connections                 — link a new connection
 *   GET    /api/v1/accounts/{accountId}/connections/{connectionId}  — get a connection by ID
 *   DELETE /api/v1/accounts/{accountId}/connections/{connectionId}  — unlink a connection
 *   POST   /api/v1/accounts/{accountId}/connections/switch          — switch primary connection
 *   GET    /api/v1/connections/{number}                            — get a connection by number
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Connections", description = "Linked-connection management and primary switching")
public class ConnectionController {

    private final AccountService accountService;
    private final ConnectionService connectionService;
    private final EntitlementService entitlementService;

    /**
     * List all connections for an account.
     */
    @GetMapping("/api/v1/accounts/{accountId}/connections")
    @Operation(summary = "List connections for an account")
    public ResponseEntity<ApiResponse<List<Connection>>> listConnections(@PathVariable String accountId) {
        List<Connection> connections = connectionService.listConnections(accountId);
        return ResponseEntity.ok(ApiResponse.of(connections, TenantContext.get().getCorrelationId()));
    }

    /**
     * Link a new connection to an account.
     */
    @PostMapping("/api/v1/accounts/{accountId}/connections")
    @Operation(summary = "Link a new connection to an account")
    public ResponseEntity<ApiResponse<Connection>> linkConnection(
            @PathVariable String accountId,
            @Valid @RequestBody ConnectionRequest request) {
        Connection connection = Connection.builder()
                .connectionId(request.connectionId() != null ? request.connectionId() : java.util.UUID.randomUUID().toString())
                .number(request.number())
                .lob(request.lob())
                .connectionType(request.connectionType())
                .displayName(request.displayName())
                .operatorAttributes(request.operatorAttributes())
                .status("ACTIVE")
                .isPrimary(false)
                .linkedAt(java.time.Instant.now())
                .build();

        Connection saved = accountService.linkConnection(accountId, connection);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(saved, TenantContext.get().getCorrelationId()));
    }

    /**
     * Get a single connection by its ID.
     */
    @GetMapping("/api/v1/accounts/{accountId}/connections/{connectionId}")
    @Operation(summary = "Get connection by ID")
    public ResponseEntity<ApiResponse<Connection>> getConnection(
            @PathVariable String accountId,
            @PathVariable String connectionId) {
        Connection connection = connectionService.getById(connectionId);
        // Verify it belongs to the account in the path
        if (!connection.getAccountId().equals(accountId)) {
            throw new com.omobio.platform.common.web.NotFoundException("Connection", connectionId);
        }
        return ResponseEntity.ok(ApiResponse.of(connection, TenantContext.get().getCorrelationId()));
    }

    /**
     * Unlink (remove) a connection from an account.
     */
    @DeleteMapping("/api/v1/accounts/{accountId}/connections/{connectionId}")
    @Operation(summary = "Unlink a connection from an account")
    public ResponseEntity<ApiResponse<Void>> unlinkConnection(
            @PathVariable String accountId,
            @PathVariable String connectionId) {
        accountService.unlinkConnection(accountId, connectionId);
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }

    /**
     * Switch the active (primary) connection for the account.
     */
    @PostMapping("/api/v1/accounts/{accountId}/connections/switch")
    @Operation(summary = "Switch the primary connection for an account")
    public ResponseEntity<ApiResponse<Connection>> switchPrimary(
            @PathVariable String accountId,
            @Valid @RequestBody SwitchPrimaryRequest request) {
        Connection connection = accountService.switchPrimaryConnection(accountId, request.connectionId());
        return ResponseEntity.ok(ApiResponse.of(connection, TenantContext.get().getCorrelationId()));
    }

    /**
     * Switch the active connection for the current session.
     * Performs entitlement check before returning.
     */
    @PostMapping("/api/v1/accounts/{accountId}/connections/{connectionId}/active")
    @Operation(summary = "Set the active connection for the session (with entitlement check)")
    public ResponseEntity<ApiResponse<Connection>> setActive(
            @PathVariable String accountId,
            @PathVariable String connectionId) {
        Connection connection = entitlementService.switchActiveConnection(accountId, connectionId);
        return ResponseEntity.ok(ApiResponse.of(connection, TenantContext.get().getCorrelationId()));
    }

    /**
     * Get a connection by its number/MSISDN.
     */
    @GetMapping("/api/v1/connections/by-number/{number}")
    @Operation(summary = "Get connection by number/MSISDN")
    public ResponseEntity<ApiResponse<Connection>> getByNumber(@PathVariable String number) {
        Connection connection = connectionService.getByNumber(number);
        return ResponseEntity.ok(ApiResponse.of(connection, TenantContext.get().getCorrelationId()));
    }

    // ------------------------------------------------------------------
    // Request records
    // ------------------------------------------------------------------

    /** Request body for linking a new connection. */
    public record ConnectionRequest(
            String connectionId,
            @NotBlank String number,
            @NotBlank String lob,
            String connectionType,
            String displayName,
            String operatorAttributes
    ) {}

    /** Request body for switching the primary connection. */
    public record SwitchPrimaryRequest(
            @NotBlank String connectionId
    ) {}
}
