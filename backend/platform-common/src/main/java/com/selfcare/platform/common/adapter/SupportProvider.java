package com.selfcare.platform.common.adapter;

import java.time.Instant;
import java.util.List;

/**
 * Support Provider contract for telco and insurance industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, AIA, ...) implements this interface
 * to expose customer support / helpdesk capabilities required by the
 * support-service and BFFs:
 *
 * <ul>
 *   <li>Create a support ticket / service request</li>
 *   <li>List existing tickets for a customer</li>
 *   <li>Get the current status of a ticket</li>
 *   <li>Add a message / comment to an existing ticket</li>
 *   <li>Resolve or close a ticket</li>
 * </ul>
 *
 * This interface is designed to work across both telco and insurance packs,
 * using a generic {@code category} field to distinguish ticket types.
 *
 * All methods take {@code tenantId} as the first argument.
 */
public interface SupportProvider extends ApiAdapter {

    /**
     * Create a new support ticket / service request.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param customerId the customer identifier
     * @param request the ticket creation request
     * @return creation result with ticket ID
     */
    TicketResult createTicket(String tenantId, String customerId, TicketRequest request);

    /**
     * List all tickets for a customer.
     *
     * @param tenantId the tenant identifier
     * @param customerId the customer identifier
     * @return list of tickets, newest first
     */
    List<SupportTicket> getTickets(String tenantId, String customerId);

    /**
     * Get a specific ticket by its ID.
     *
     * @param tenantId the tenant identifier
     * @param ticketId the ticket ID
     * @return the ticket, or null if not found
     */
    SupportTicket getTicket(String tenantId, String ticketId);

    /**
     * Add a comment / message to an existing ticket.
     *
     * @param tenantId the tenant identifier
     * @param ticketId the ticket ID
     * @param message the message to add
     * @return result
     */
    TicketResult addMessage(String tenantId, String ticketId, String message);

    /**
     * Change the status of a ticket (e.g. resolve, close, escalate).
     *
     * @param tenantId the tenant identifier
     * @param ticketId the ticket ID
     * @param newStatus the new status
     * @param resolution optional resolution description
     * @return result
     */
    TicketResult updateTicketStatus(String tenantId, String ticketId,
                                   String newStatus, String resolution);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Request to create a new support ticket.
     */
    record TicketRequest(
            String subject,
            String category,        // BILLING, TECHNICAL, GENERAL, COMPLAINT, PORTING, CLAIM, ...
            String priority,        // LOW, MEDIUM, HIGH, URGENT
            String description,
            String connectionId,    // optional; ties ticket to a specific connection
            String productCode,     // optional; relevant product
            List<String> attachments // optional; file IDs already uploaded via DocumentProvider
    ) {}

    /**
     * Result of a ticket mutation (create, message, status change).
     */
    record TicketResult(
            boolean success,
            String ticketId,
            String status,
            String failureReason
    ) {}

    /**
     * A support ticket.
     */
    record SupportTicket(
            String ticketId,
            String customerId,
            String subject,
            String category,
            String priority,
            String status,          // OPEN, IN_PROGRESS, AWAITING_CUSTOMER, RESOLVED, CLOSED
            Instant createdAt,
            Instant updatedAt,
            String assignedTo,
            String resolution,
            List<TicketMessage> messages
    ) {}

    /**
     * A message within a support ticket.
     */
    record TicketMessage(
            String messageId,
            String ticketId,
            Instant timestamp,
            String author,          // CUSTOMER, AGENT, SYSTEM
            String content,
            List<String> attachments
    ) {}
}
