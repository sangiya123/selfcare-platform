package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.SupportProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogSupportProviderTest {

    @Mock
    private DialogHttpClient httpClient;

    private DialogSupportProvider provider;

    private static final String TENANT = "dialog-lk";
    private static final String CONN = "94777123456";
    private static final String BASE = "https://bss-mock.selfcare.io/api";
    private static final String CREATE_PATH =
            "/apicall/crm/api/cmu/v1.0.1/cmuenterprise/addComplaint/MyDialog/SCAPP";
    private static final String LIST_PATH = "/apicall/crm/SblSrInfo/v1.0.0/sendRequest";

    @BeforeEach
    void setUp() {
        provider = new DialogSupportProvider(httpClient);
    }

    private DialogHttpClient.DialogConfig validConfig() {
        return new DialogHttpClient.DialogConfig(
                TENANT, BASE, null, null, "dev-key",
                null, null, null);
    }

    @Test
    @DisplayName("getAdapterId returns dialog-lk")
    void adapterId() {
        assertEquals("dialog-lk", provider.getAdapterId());
    }

    @Test
    @DisplayName("returns failure when Dialog BSS is not configured")
    void noConfigReturnsFailure() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS")))
                .thenReturn(DialogHttpClient.DialogConfig.empty(TENANT));

        SupportProvider.TicketResult result = provider.createTicket(TENANT, "112233445",
                new SupportProvider.TicketRequest("Broken", "TECHNICAL", "HIGH",
                        "No signal", CONN, null, List.of()));

        assertNotNull(result);
        assertFalse(result.success());
        assertNull(result.ticketId());
    }

    @Test
    @DisplayName("creates a service request and parses the SR number")
    void createTicketSuccess() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS"))).thenReturn(validConfig());
        when(httpClient.put(eq(TENANT), eq(BASE), eq(CREATE_PATH), org.mockito.ArgumentMatchers.anyMap(),
                isNull(), eq("dev-key")))
                .thenReturn(Map.of(
                        "status", "success",
                        "errorCode", "00",
                        "data", Map.of("complaintResponse", Map.of("sr_number", "1-987654321"))));

        SupportProvider.TicketResult result = provider.createTicket(TENANT, "112233445",
                new SupportProvider.TicketRequest("Broken", "TECHNICAL", "HIGH",
                        "No signal", CONN, null, List.of()));

        assertTrue(result.success());
        assertEquals("1-987654321", result.ticketId());
        assertEquals("OPEN", result.status());
    }

    @Test
    @DisplayName("lists service requests from a grouped sendRequest response")
    void getTicketsParsesGroups() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS"))).thenReturn(validConfig());
        when(httpClient.post(eq(TENANT), eq(BASE), eq(LIST_PATH), org.mockito.ArgumentMatchers.anyMap(),
                isNull(), eq("dev-key")))
                .thenReturn(Map.of(
                        "statusCode", "000",
                        "response", List.of(List.of(Map.of(
                                "srId", "1-101",
                                "customerRefNo", CONN,
                                "lob", "MOBILE",
                                "area", "BILLING & CHARGING",
                                "subArea", "MOBILE SERVICE REQUEST",
                                "solution", "",
                                "status", "Open",
                                "owner", "SADMIN",
                                "createdDate", "01-08-2026 10:30:00"),
                                Map.of(
                                        "srId", "1-102",
                                        "customerRefNo", CONN,
                                        "lob", "MOBILE",
                                        "area", "SERVICE RELATED",
                                        "status", "Closed",
                                        "owner", "SADMIN",
                                        "createdDate", "15-07-2026 09:00:00")))));

        List<SupportProvider.SupportTicket> tickets = provider.getTickets(TENANT, CONN);

        assertEquals(2, tickets.size());
        assertEquals("1-101", tickets.get(0).ticketId());
        assertEquals("OPEN", tickets.get(0).status());
        assertEquals("1-102", tickets.get(1).ticketId());
        assertEquals("CLOSED", tickets.get(1).status());
        assertEquals(Instant.parse("2026-08-01T10:30:00Z"), tickets.get(0).createdAt());
    }

    @Test
    @DisplayName("addMessage is unsupported on Dialog service requests")
    void addMessageUnsupported() {
        SupportProvider.TicketResult result = provider.addMessage(TENANT, "1-101", "hello");

        assertNotNull(result);
        assertFalse(result.success());
        assertNotNull(result.failureReason());
    }

    @Test
    @DisplayName("cancels a service request via WomWorkOrder")
    void cancelTicket() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS"))).thenReturn(validConfig());
        when(httpClient.put(eq(TENANT), eq(BASE),
                eq("/apicall/WomWorkOrder/v2.0"), org.mockito.ArgumentMatchers.anyMap(),
                isNull(), eq("dev-key")))
                .thenReturn(Map.of("status", "201"));

        SupportProvider.TicketResult result =
                provider.updateTicketStatus(TENANT, "1-101", "CANCELLED", null);

        assertTrue(result.success());
        assertEquals("CANCELLED", result.status());
    }
}