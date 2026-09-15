package com.selfcare.support.web;

import com.selfcare.platform.common.adapter.SupportProvider;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.platform.common.web.UnauthorizedException;
import com.selfcare.support.service.SupportService;
import com.selfcare.support.web.dto.TicketRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Support REST API.
 *
 * <p>Endpoints:
 *   GET  /api/v1/support/tickets                  — list tickets for a customer/connection
 *   GET  /api/v1/support/tickets/{ticketId}       — single ticket detail
 *   POST /api/v1/support/tickets                  — create a ticket
 *   POST /api/v1/support/tickets/{ticketId}/messages — add a message
 *   PUT  /api/v1/support/tickets/{ticketId}/status   — change ticket status
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Service requests / support tickets")
public class SupportController {

    private final SupportService supportService;

    private String currentConnectionId() {
        String userId = TenantContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("No authenticated user");
        }
        return userId.startsWith("MSISDN:") ? userId.substring("MSISDN:".length()) : userId;
    }

    @GetMapping("/tickets")
    @Operation(summary = "List tickets", description = "Returns service requests for a customer or connection")
    public ResponseEntity<ApiResponse<List<SupportProvider.SupportTicket>>> getTickets(
            @RequestParam(required = false) String connectionId) {
        String resolved = connectionId != null && !connectionId.isBlank()
                ? connectionId : currentConnectionId();
        return ResponseEntity.ok(ApiResponse.of(
                supportService.getTickets(TenantContext.get().getTenantId(), resolved),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Get ticket detail", description = "Returns a single service request")
    public ResponseEntity<ApiResponse<SupportProvider.SupportTicket>> getTicket(
            @PathVariable String ticketId) {
        return ResponseEntity.ok(ApiResponse.of(
                supportService.getTicket(TenantContext.get().getTenantId(), ticketId),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/tickets")
    @Operation(summary = "Create ticket", description = "Creates a new service request")
    public ResponseEntity<ApiResponse<SupportProvider.TicketResult>> createTicket(
            @RequestBody TicketRequestDto dto) {
        String connectionId = dto.connectionId() != null && !dto.connectionId().isBlank()
                ? dto.connectionId() : currentConnectionId();
        SupportProvider.TicketRequest request = new SupportProvider.TicketRequest(
                dto.subject(), dto.category(), dto.priority(), dto.description(),
                connectionId, dto.productCode(), dto.attachments());
        return ResponseEntity.ok(ApiResponse.of(
                supportService.createTicket(TenantContext.get().getTenantId(), connectionId, request),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/tickets/{ticketId}/messages")
    @Operation(summary = "Add message", description = "Adds a customer message to a service request")
    public ResponseEntity<ApiResponse<SupportProvider.TicketResult>> addMessage(
            @PathVariable String ticketId,
            @RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            throw new UnauthorizedException("Message is required");
        }
        return ResponseEntity.ok(ApiResponse.of(
                supportService.addMessage(TenantContext.get().getTenantId(), ticketId, message),
                TenantContext.get().getCorrelationId()));
    }

    @PutMapping("/tickets/{ticketId}/status")
    @Operation(summary = "Change ticket status", description = "Cancels/resolves/closes a service request where the operator supports it")
    public ResponseEntity<ApiResponse<SupportProvider.TicketResult>> updateStatus(
            @PathVariable String ticketId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new UnauthorizedException("Status is required");
        }
        return ResponseEntity.ok(ApiResponse.of(
                supportService.updateTicketStatus(TenantContext.get().getTenantId(), ticketId,
                        status, body.get("resolution")),
                TenantContext.get().getCorrelationId()));
    }
}