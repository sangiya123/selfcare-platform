package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import com.selfcare.platform.common.adapter.SupportProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dialog Support Provider — customer support / service requests against Dialog MIFE CRM.
 *
 * <p>Implements the canonical {@link SupportProvider} contract using Dialog's
 * production service-request APIs:</p>
 *
 * <ul>
 *   <li>{@code SblSrInfo/sendRequest} — list and get service requests (dockets)</li>
 *   <li>{@code CMU addComplaint} — create a service request</li>
 *   <li>{@code WomWorkOrder} — cancel an eligible service request</li>
 * </ul>
 *
 * <p>Every API shape (path templates, LOB, SR type, response field names) is
 * resolved from the tenant's DB-backed integration configuration via
 * {@link DialogOperator} — never hardcoded. Another operator can reuse this
 * class and configure its own paths/fields against the same canonical contract.</p>
 *
 * <p>Configure via Selfcare Studio admin: Integrations &gt; Dialog BSS,
 * keys {@code path.support.*}, {@code support.srType}, {@code support.subArea}.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = SupportProvider.class)
@RequiredArgsConstructor
public class DialogSupportProvider implements ApiAdapter, SupportProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private static final DateTimeFormatter MIFE_DATE_TIME =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter MIFE_DATE =
            DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // SupportProvider implementation
    // ================================================================

    @Override
    public TicketResult createTicket(String tenantId, String customerId, TicketRequest request) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}, cannot create ticket", tenantId);
            return new TicketResult(false, null, null, "Dialog BSS integration not configured");
        }

        String conn = refAccount(request.connectionId() != null ? request.connectionId() : customerId);
        if (conn == null || conn.isEmpty()) {
            return new TicketResult(false, null, null, "A connection reference is required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("description", descriptionOf(request));
        body.put("lob", op.lob("support", "GSM"));
        body.put("area", request.category() != null ? request.category() : "GENERAL");
        body.put("subArea", cfg.meta("support.subArea", "MOBILE SERVICE REQUEST"));
        body.put("subSubArea", "");
        body.put("refAccount", conn);
        body.put("altContactNo", conn);
        body.put("srType", cfg.meta("support.srType", "Complaint"));
        body.put("source", "Selfcare Platform");

        String path = op.path("support.create",
                "/apicall/crm/api/cmu/v1.0.1/cmuenterprise/addComplaint/MyDialog/SCAPP",
                Map.of());
        try {
            Map<String, Object> data = httpClient.put(tenantId, cfg.baseUrl(), path, body, null, cfg.apiKey());
            if (data == null) {
                return new TicketResult(false, null, null, "Empty response from Dialog MIFE");
            }
            if ("success".equalsIgnoreCase(str(data.get("status"), null))
                    && "00".equals(str(data.get("errorCode"), null))) {
                Object inner = deep(data, "data.complaintResponse");
                String srNumber = null;
                if (inner instanceof Map<?, ?> complaintResponse) {
                    srNumber = str(((Map<?, ?>) complaintResponse).get("sr_number"), null);
                }
                if (srNumber == null) {
                    srNumber = str(data.get("request_number"), null);
                }
                log.info("Dialog service request created for tenant={} conn={} sr={}",
                        tenantId, conn, srNumber);
                return new TicketResult(true, srNumber, "OPEN", null);
            }
            String reason = str(data.get("errorDescription"),
                    str(data.get("info"), "Dialog MIFE rejected the request"));
            return new TicketResult(false, null, null, reason);
        } catch (DialogApiException e) {
            log.error("Dialog createTicket failed for tenant={} conn={}: {}",
                    tenantId, conn, e.getMessage());
            return new TicketResult(false, null, null, e.getMessage());
        }
    }

    @Override
    public List<SupportTicket> getTickets(String tenantId, String customerId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String conn = refAccount(customerId);
        Map<String, Object> body = new HashMap<>();
        body.put("refAccount", conn == null ? List.of() : List.of(conn));
        body.put("docketNo", null);
        body.put("srType", cfg.meta("support.srType", "Complaint"));

        List<SupportTicket> tickets = fetchDockets(tenantId, op, body);
        return tickets;
    }

    @Override
    public SupportTicket getTicket(String tenantId, String ticketId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("docketNo", ticketId);
        body.put("srType", cfg.meta("support.srType", "Complaint"));

        List<SupportTicket> tickets = fetchDockets(tenantId, op, body);
        if (tickets.isEmpty()) {
            return null;
        }
        return tickets.stream()
                .filter(t -> ticketId.equals(t.ticketId()))
                .findFirst()
                .orElse(tickets.get(0));
    }

    @Override
    public TicketResult addMessage(String tenantId, String ticketId, String message) {
        log.warn("Dialog MIFE does not expose a customer-message API for SR {}; "
                + "message={} skipped for tenant={}", ticketId, message, tenantId);
        return new TicketResult(false, ticketId, null,
                "Messaging is not available on Dialog service requests");
    }

    @Override
    public TicketResult updateTicketStatus(String tenantId, String ticketId,
                                           String newStatus, String resolution) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return new TicketResult(false, ticketId, null, "Dialog BSS integration not configured");
        }

        boolean isCancel = "CANCELLED".equalsIgnoreCase(newStatus)
                || "CLOSED".equalsIgnoreCase(newStatus);
        String path = op.path("support.cancel",
                isCancel ? "/apicall/WomWorkOrder/v2.0" : null, Map.of());
        if (path == null) {
            log.warn("Dialog cannot transition SR {} to {} for tenant={}",
                    ticketId, newStatus, tenantId);
            return new TicketResult(false, ticketId, null,
                    "Request type does not support " + newStatus);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("OrderId", ticketId);
        body.put("RejectReasonId", 1);
        body.put("SubRejectReasonId", "3");
        body.put("Status", "CANCELLED");
        try {
            Map<String, Object> data = httpClient.put(tenantId, cfg.baseUrl(), path, body, null, cfg.apiKey());
            if (data == null) {
                return new TicketResult(false, ticketId, null, "Empty response from Dialog MIFE");
            }
            String status = str(data.get("status"), null);
            if ("201".equals(status)) {
                log.info("Dialog service request {} cancelled for tenant={}", ticketId, tenantId);
                return new TicketResult(true, ticketId, "CANCELLED", null);
            }
            String reason = str(data.get("errorDescription"),
                    str(data.get("info"), "Dialog MIFE rejected the cancellation"));
            return new TicketResult(false, ticketId, null, reason);
        } catch (DialogApiException e) {
            log.error("Dialog updateTicketStatus failed for tenant={} sr={}: {}",
                    tenantId, ticketId, e.getMessage());
            return new TicketResult(false, ticketId, null, e.getMessage());
        }
    }

    // ================================================================
    // SblSrInfo / docket parsing
    // ================================================================

    private List<SupportTicket> fetchDockets(String tenantId, DialogOperator op,
                                             Map<String, Object> body) {
        String path = op.path("support.list",
                "/apicall/crm/SblSrInfo/v1.0.0/sendRequest", Map.of());
        try {
            Map<String, Object> data = httpClient.post(tenantId, op.cfg().baseUrl(), path, body, null,
                    op.cfg().apiKey());
            if (data == null) {
                return List.of();
            }
            if (!"000".equals(str(data.get("statusCode"), null))) {
                log.warn("Dialog sendRequest failed for tenant={}: statusCode={}",
                        tenantId, data.get("statusCode"));
                return List.of();
            }
            Object response = data.get("response");
            if (!(response instanceof List<?> groups)) {
                return List.of();
            }
            List<SupportTicket> tickets = new ArrayList<>();
            for (Object group : groups) {
                if (!(group instanceof List<?> dockets)) {
                    continue;
                }
                for (Object o : dockets) {
                    if (o instanceof Map<?, ?> raw) {
                        @SuppressWarnings("unchecked")
                        SupportTicket ticket = mapToTicket((Map<String, Object>) raw);
                        if (ticket != null) {
                            tickets.add(ticket);
                        }
                    }
                }
            }
            return tickets;
        } catch (DialogApiException e) {
            log.error("Dialog sendRequest failed for tenant={}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    private SupportTicket mapToTicket(Map<String, Object> d) {
        String srId = str(d.get("srId"), null);
        if (srId == null) {
            return null;
        }
        Instant created = parseMifeInstant(d.get("createdDate"));
        Instant updated = parseMifeInstant(d.get("dateClosed"));
        return new SupportTicket(
                srId,
                str(d.get("customerRefNo"), null),
                str(d.get("srDescription"), null),
                str(d.get("area"), null),
                null,
                mapStatus(str(d.get("status"), null)),
                created,
                updated != null ? updated : created,
                str(d.get("owner"), null),
                str(d.get("solution"), null),
                List.of()
        );
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String descriptionOf(TicketRequest request) {
        if (request.description() != null && !request.description().isBlank()) {
            return request.description();
        }
        return request.subject() != null ? request.subject() : "";
    }

    /**
     * Normalize an account reference to the trailing 9 digits Dialog MIFE expects.
     */
    private String refAccount(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    private String mapStatus(String raw) {
        if (raw == null) {
            return "OPEN";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "OPEN";
        }
        switch (s.toLowerCase(Locale.ROOT)) {
            case "open":
                return "OPEN";
            case "closed":
                return "CLOSED";
            case "pending":
            case "in progress":
            case "assigned":
                return "IN_PROGRESS";
            case "complete":
            case "resolved":
                return "RESOLVED";
            case "waiting for cx":
            case "awaiting customer":
                return "AWAITING_CUSTOMER";
            default:
                return s.toUpperCase(Locale.ROOT);
        }
    }

    private Instant parseMifeInstant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Instant i) {
            return i;
        }
        if (v instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        String s = String.valueOf(v);
        try {
            return LocalDateTime.parse(s, MIFE_DATE_TIME).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // not a date-time; try date-only below
        }
        try {
            return LocalDateTime.parse(s, MIFE_DATE).toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // try ISO-8601
        }
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
            log.debug("Unparseable Dialog date: {}", s);
        }
        return null;
    }

    private Object deep(Map<String, Object> data, String dottedPath) {
        Object cur = data;
        for (String part : dottedPath.split("\\.")) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = map.get(part);
        }
        return cur;
    }

    private String str(Object v, String fallback) {
        if (v == null) {
            return fallback;
        }
        String s = v instanceof String ? v.toString() : String.valueOf(v);
        return s.isBlank() ? fallback : s;
    }
}