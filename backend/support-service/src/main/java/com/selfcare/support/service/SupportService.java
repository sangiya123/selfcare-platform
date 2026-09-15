package com.selfcare.support.service;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.SupportProvider;
import com.selfcare.platform.common.web.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Support service — resolves the tenant's {@link SupportProvider} from the
 * adapter registry and dispatches service-request operations to it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final ApiAdapterRegistry<SupportProvider> providerRegistry;

    /**
     * Create a new support ticket / service request.
     *
     * @param tenantId    the tenant identifier
     * @param customerId  the customer (or connection) reference
     * @param request     the ticket creation request
     * @return the creation result
     * @throws ServiceUnavailableException when no provider is configured or the
     *         operator rejects the request
     */
    public SupportProvider.TicketResult createTicket(String tenantId, String customerId,
                                                     SupportProvider.TicketRequest request) {
        try {
            SupportProvider provider = providerRegistry.getProvider(tenantId);
            SupportProvider.TicketResult result = provider.createTicket(tenantId, customerId, request);
            if (result == null || !result.success()) {
                throw new ServiceUnavailableException("SupportProvider",
                        result != null ? result.failureReason() : "Null response",
                        true);
            }
            return result;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Support createTicket failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            throw new ServiceUnavailableException("SupportProvider", e.getMessage(), true);
        }
    }

    /**
     * List tickets for a customer/connection.
     */
    public List<SupportProvider.SupportTicket> getTickets(String tenantId, String customerId) {
        try {
            return providerRegistry.getProvider(tenantId).getTickets(tenantId, customerId);
        } catch (Exception e) {
            log.error("Support getTickets failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            throw new ServiceUnavailableException("SupportProvider", e.getMessage(), false);
        }
    }

    /**
     * Get a single ticket by id.
     */
    public SupportProvider.SupportTicket getTicket(String tenantId, String ticketId) {
        try {
            return providerRegistry.getProvider(tenantId).getTicket(tenantId, ticketId);
        } catch (Exception e) {
            log.error("Support getTicket failed for tenant={} ticket={}: {}",
                    tenantId, ticketId, e.getMessage());
            throw new ServiceUnavailableException("SupportProvider", e.getMessage(), false);
        }
    }

    /**
     * Add a message to an existing ticket.
     */
    public SupportProvider.TicketResult addMessage(String tenantId, String ticketId, String message) {
        try {
            SupportProvider provider = providerRegistry.getProvider(tenantId);
            SupportProvider.TicketResult result = provider.addMessage(tenantId, ticketId, message);
            if (result == null || !result.success()) {
                throw new ServiceUnavailableException("SupportProvider",
                        result != null ? result.failureReason() : "Null response",
                        true);
            }
            return result;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Support addMessage failed for tenant={} ticket={}: {}",
                    tenantId, ticketId, e.getMessage());
            throw new ServiceUnavailableException("SupportProvider", e.getMessage(), true);
        }
    }

    /**
     * Change the status of a ticket (cancel/resolve/close where the operator supports it).
     */
    public SupportProvider.TicketResult updateTicketStatus(String tenantId, String ticketId,
                                                           String newStatus, String resolution) {
        try {
            SupportProvider provider = providerRegistry.getProvider(tenantId);
            SupportProvider.TicketResult result =
                    provider.updateTicketStatus(tenantId, ticketId, newStatus, resolution);
            if (result == null || !result.success()) {
                throw new ServiceUnavailableException("SupportProvider",
                        result != null ? result.failureReason() : "Null response",
                        true);
            }
            return result;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Support updateTicketStatus failed for tenant={} ticket={}: {}",
                    tenantId, ticketId, e.getMessage());
            throw new ServiceUnavailableException("SupportProvider", e.getMessage(), true);
        }
    }
}