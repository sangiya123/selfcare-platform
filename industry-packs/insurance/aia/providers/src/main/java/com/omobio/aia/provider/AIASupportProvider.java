package com.omobio.aia.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.adapter.SupportProvider;
import com.omobio.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AIA Support Provider — manages customer support tickets for AIA insurance customers.
 *
 * <p>Implements the canonical {@link SupportProvider} contract from platform-common.
 * Supports ticket creation, listing, status updates, and conversation
 * threading across all six AIA market tenants (aia-lk, aia-sg, aia-th, aia-my,
 * aia-hk, aia-in).</p>
 *
 * <p>Per-tenant config (base URL, clientId, clientSecret, apiKey) is loaded
 * at runtime from {@link TenantConfigurationService}. Configure via
 * Selfcare Studio admin: Integrations &gt; AIA Insurance.</p>
 *
 * <p>Mock mode (default in dev/test) returns realistic AIA-prefixed ticket data
 * tailored to the tenant's country.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "aia-lk", providerInterface = SupportProvider.class)
@RequiredArgsConstructor
public class AIASupportProvider implements ApiAdapter, SupportProvider {

    private static final String INTEGRATION_TYPE = "AIA_INSURANCE";
    private static final String ADAPTER_ID = "aia-lk";

    private final TenantConfigurationService tenantConfig;
    private final AIAHttpClient httpClient;

    @Value("${omobio.aia.mock-mode:true}")
    private boolean mockMode;

    @Value("${omobio.tenant.default-id:aia-lk}")
    private String defaultTenantId;

    // Per-tenant config cache
    private final Map<String, AIAInsuranceProvider.Config> configCache = new ConcurrentHashMap<>();
    // Per-tenant mock state
    private final Map<String, MockSupportState> mockState = new ConcurrentHashMap<>();

    @Override
    public String getAdapterId() {
        return defaultTenantId;
    }

    // ================================================================
    // Configuration
    // ================================================================

    private AIAInsuranceProvider.Config loadConfig(String tenantId) {
        return configCache.computeIfAbsent(tenantId, this::resolveConfig);
    }

    private AIAInsuranceProvider.Config resolveConfig(String tenantId) {
        return tenantConfig.getIntegration(tenantId, INTEGRATION_TYPE)
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(c -> new AIAInsuranceProvider.Config(
                        tenantId,
                        c.getBaseUrl(),
                        c.getCredential("clientId"),
                        c.getCredential("clientSecret"),
                        c.getCredential("apiKey"),
                        c.getMetadata()))
                .orElseGet(() -> AIAInsuranceProvider.Config.empty(tenantId));
    }

    // ================================================================
    // SupportProvider implementation
    // ================================================================

    @Override
    public TicketResult createTicket(String tenantId, String customerId, TicketRequest request) {
        if (mockMode) {
            return createTicketMock(tenantId, customerId, request);
        }

        AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return new TicketResult(false, null, null, "No AIA Insurance integration configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("subject", request.subject());
        body.put("category", request.category());
        body.put("priority", request.priority());
        body.put("description", request.description());
        if (request.connectionId() != null) body.put("connectionId", request.connectionId());
        if (request.productCode() != null) body.put("productCode", request.productCode());
        if (request.attachments() != null) body.put("attachments", request.attachments());

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/api/v1/support/tickets", body, token);
            if (data == null) {
                return new TicketResult(false, null, null, "Empty response from AIA");
            }
            Object inner = data.get("data");
            if (inner instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) d;
                return new TicketResult(
                        true,
                        (String) m.get("ticketId"),
                        (String) m.get("status"),
                        null
                );
            }
            return new TicketResult(false, null, null, "Unexpected response shape");
        } catch (Exception e) {
            log.error("AIASupportProvider.createTicket failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return new TicketResult(false, null, null, e.getMessage());
        }
    }

    @Override
    public List<SupportTicket> getTickets(String tenantId, String customerId) {
        if (mockMode) {
            return getTicketsMock(tenantId, customerId);
        }

        AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return List.of();
        }

        var queryParams = Map.<String, Object>of("customerId", customerId);
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            List<Map<String, Object>> data = httpClient.getList(tenantId, cfg.baseUrl(),
                    "/api/v1/support/tickets", queryParams, token);
            return data.stream().map(this::mapToTicket).toList();
        } catch (Exception e) {
            log.error("AIASupportProvider.getTickets failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public SupportTicket getTicket(String tenantId, String ticketId) {
        if (mockMode) {
            return getTicketMock(tenantId, ticketId);
        }

        AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return null;
        }

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(),
                    "/api/v1/support/tickets/" + ticketId, null, token);
            if (data == null) return null;
            Object inner = data.get("data");
            if (inner instanceof Map<?, ?> d) {
                return mapToTicket((Map<String, Object>) d);
            }
            return null;
        } catch (Exception e) {
            log.error("AIASupportProvider.getTicket failed for tenant={} ticketId={}: {}",
                    tenantId, ticketId, e.getMessage());
            return null;
        }
    }

    @Override
    public TicketResult addMessage(String tenantId, String ticketId, String message) {
        if (mockMode) {
            return addMessageMock(tenantId, ticketId, message);
        }

        AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return new TicketResult(false, null, null, "No AIA Insurance integration configured");
        }

        Map<String, Object> body = Map.of(
                "author", "CUSTOMER",
                "content", message
        );
        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.post(tenantId, cfg.baseUrl(),
                    "/api/v1/support/tickets/" + ticketId + "/messages", body, token);
            if (data == null) {
                return new TicketResult(false, ticketId, null, "Empty response");
            }
            return new TicketResult(true, ticketId, "IN_PROGRESS", null);
        } catch (Exception e) {
            log.error("AIASupportProvider.addMessage failed for tenant={} ticketId={}: {}",
                    tenantId, ticketId, e.getMessage());
            return new TicketResult(false, ticketId, null, e.getMessage());
        }
    }

    @Override
    public TicketResult updateTicketStatus(String tenantId, String ticketId,
                                           String newStatus, String resolution) {
        if (mockMode) {
            return updateTicketStatusMock(tenantId, ticketId, newStatus, resolution);
        }

        AIAInsuranceProvider.Config cfg = loadConfig(tenantId);
        if (!cfg.isValid()) {
            log.warn("AIA Insurance not configured for tenant={}", tenantId);
            return new TicketResult(false, null, null, "No AIA Insurance integration configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", newStatus);
        if (resolution != null) body.put("resolution", resolution);

        try {
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> data = httpClient.put(tenantId, cfg.baseUrl(),
                    "/api/v1/support/tickets/" + ticketId, body, token);
            if (data == null) {
                return new TicketResult(false, ticketId, null, "Empty response");
            }
            return new TicketResult(true, ticketId, newStatus, null);
        } catch (Exception e) {
            log.error("AIASupportProvider.updateTicketStatus failed for tenant={} ticketId={}: {}",
                    tenantId, ticketId, e.getMessage());
            return new TicketResult(false, ticketId, null, e.getMessage());
        }
    }

    // ================================================================
    // Mapping (live mode)
    // ================================================================

    private SupportTicket mapToTicket(Map<String, Object> m) {
        Object messagesObj = m.get("messages");
        List<TicketMessage> messages = List.of();
        if (messagesObj instanceof List<?> list) {
            messages = list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<TicketMessage>map(m2 -> new TicketMessage(
                            (String) m2.get("messageId"),
                            (String) m.get("ticketId"),
                            parseInstant(m2.get("timestamp")),
                            (String) m2.get("author"),
                            (String) m2.get("content"),
                            (List<String>) m2.get("attachments")
                    ))
                    .toList();
        }
        return new SupportTicket(
                (String) m.get("ticketId"),
                (String) m.get("customerId"),
                (String) m.get("subject"),
                (String) m.get("category"),
                (String) m.get("priority"),
                (String) m.get("status"),
                parseInstant(m.get("createdAt")),
                parseInstant(m.get("updatedAt")),
                (String) m.get("assignedTo"),
                (String) m.get("resolution"),
                messages
        );
    }

    // ================================================================
    // Mock mode helpers
    // ================================================================

    private TicketResult createTicketMock(String tenantId, String customerId, TicketRequest request) {
        MockSupportState state = getOrCreateMockState(tenantId);
        int seq = state.ticketSeq.incrementAndGet();
        String ticketId = "AIA-TKT-" + seq;
        Instant now = Instant.now();
        TicketMessage firstMessage = new TicketMessage(
                "AIA-MSG-" + seq + "-1",
                ticketId,
                now,
                "CUSTOMER",
                request.description() != null ? request.description() : "",
                request.attachments() != null ? request.attachments() : List.of()
        );
        SupportTicket ticket = new SupportTicket(
                ticketId,
                customerId,
                request.subject() != null ? request.subject() : "Support request",
                request.category() != null ? request.category() : "GENERAL",
                request.priority() != null ? request.priority() : "MEDIUM",
                "OPEN",
                now,
                now,
                null,
                null,
                List.of(firstMessage)
        );
        state.tickets.put(ticketId, ticket);
        return new TicketResult(true, ticketId, "OPEN", null);
    }

    private List<SupportTicket> getTicketsMock(String tenantId, String customerId) {
        MockSupportState state = getOrCreateMockState(tenantId);
        return state.tickets.values().stream()
                .filter(t -> t.customerId().equals(customerId))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    private SupportTicket getTicketMock(String tenantId, String ticketId) {
        MockSupportState state = getOrCreateMockState(tenantId);
        return state.tickets.get(ticketId);
    }

    private TicketResult addMessageMock(String tenantId, String ticketId, String message) {
        MockSupportState state = getOrCreateMockState(tenantId);
        SupportTicket existing = state.tickets.get(ticketId);
        if (existing == null) {
            return new TicketResult(false, ticketId, null, "Ticket not found");
        }
        int msgSeq = existing.messages().size() + 1;
        TicketMessage newMessage = new TicketMessage(
                "AIA-MSG-" + ticketId + "-" + msgSeq,
                ticketId,
                Instant.now(),
                "CUSTOMER",
                message,
                List.of()
        );
        List<TicketMessage> updatedMessages = new java.util.ArrayList<>(existing.messages());
        updatedMessages.add(newMessage);
        SupportTicket updated = new SupportTicket(
                existing.ticketId(),
                existing.customerId(),
                existing.subject(),
                existing.category(),
                existing.priority(),
                "IN_PROGRESS",
                existing.createdAt(),
                Instant.now(),
                existing.assignedTo(),
                existing.resolution(),
                updatedMessages
        );
        state.tickets.put(ticketId, updated);
        return new TicketResult(true, ticketId, "IN_PROGRESS", null);
    }

    private TicketResult updateTicketStatusMock(String tenantId, String ticketId,
                                                 String newStatus, String resolution) {
        MockSupportState state = getOrCreateMockState(tenantId);
        SupportTicket existing = state.tickets.get(ticketId);
        if (existing == null) {
            return new TicketResult(false, ticketId, null, "Ticket not found");
        }
        SupportTicket updated = new SupportTicket(
                existing.ticketId(),
                existing.customerId(),
                existing.subject(),
                existing.category(),
                existing.priority(),
                newStatus,
                existing.createdAt(),
                Instant.now(),
                existing.assignedTo(),
                resolution != null ? resolution : existing.resolution(),
                existing.messages()
        );
        state.tickets.put(ticketId, updated);
        return new TicketResult(true, ticketId, newStatus, null);
    }

    private MockSupportState getOrCreateMockState(String tenantId) {
        return mockState.computeIfAbsent(tenantId, MockSupportState::new);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Instant parseInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (v instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Per-tenant mock state for support tickets.
     */
    private static class MockSupportState {
        final String tenantId;
        final AtomicInteger ticketSeq = new AtomicInteger(1000);
        final Map<String, SupportTicket> tickets = new ConcurrentHashMap<>();

        MockSupportState(String tenantId) {
            this.tenantId = tenantId;
        }
    }
}
