package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.CreditLimitProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DialogCreditLimitProviderTest {

    @Mock
    private DialogHttpClient httpClient;

    private DialogCreditLimitProvider provider;

    private static final String TENANT = "dialog-lk";
    private static final String CONN = "94777123456";

    @BeforeEach
    void setUp() {
        provider = new DialogCreditLimitProvider(httpClient);
    }

    private DialogHttpClient.DialogConfig validConfig() {
        return new DialogHttpClient.DialogConfig(
                TENANT, "https://bss-mock.selfcare.io/api", null, null, "dev-key",
                null, null, null);
    }

    @Test
    @DisplayName("getAdapterId returns dialog-lk")
    void adapterId() {
        assertEquals("dialog-lk", provider.getAdapterId());
    }

    @Test
    @DisplayName("returns null when Dialog BSS is not configured")
    void noConfigReturnsNull() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS")))
                .thenReturn(DialogHttpClient.DialogConfig.empty(TENANT));

        assertNull(provider.fetchCreditLimit(TENANT, CONN));
    }

    @Test
    @DisplayName("parses flat credit-limit response")
    void parsesFlatResponse() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS"))).thenReturn(validConfig());
        when(httpClient.get(eq(TENANT), eq("https://bss-mock.selfcare.io/api"),
                eq("/subscriber/credit/" + CONN), anyMap(), isNull(), eq("dev-key")))
                .thenReturn(Map.of(
                        "creditLimit", 2500,
                        "usedCredit", 900,
                        "currency", "LKR"));

        CreditLimitProvider.CreditLimit credit = provider.fetchCreditLimit(TENANT, CONN);

        assertNotNull(credit);
        assertEquals(CONN, credit.connectionId());
        assertEquals(new BigDecimal("2500.0"), credit.limitAmount());
        assertEquals(new BigDecimal("900.0"), credit.usedAmount());
        assertEquals(new BigDecimal("1600.0"), credit.availableAmount());
        assertEquals("LKR", credit.currency());
        assertTrue(credit.creditEnabled());
    }

    @Test
    @DisplayName("parses nested data payload and derives available when absent")
    void parsesNestedPayload() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS"))).thenReturn(validConfig());
        when(httpClient.get(eq(TENANT), eq("https://bss-mock.selfcare.io/api"),
                eq("/subscriber/credit/" + CONN), anyMap(), isNull(), eq("dev-key")))
                .thenReturn(Map.of("data", Map.of(
                        "creditLimit", "5000.50",
                        "usedCredit", "4200.25")));

        CreditLimitProvider.CreditLimit credit = provider.fetchCreditLimit(TENANT, CONN);

        assertNotNull(credit);
        assertEquals(new BigDecimal("5000.50"), credit.limitAmount());
        assertEquals(new BigDecimal("4200.25"), credit.usedAmount());
        assertEquals(new BigDecimal("800.25"), credit.availableAmount());
        assertTrue(credit.creditEnabled());
    }

    @Test
    @DisplayName("returns null when the BSS returns 404 (no subscriber)")
    void notFoundReturnsNull() {
        when(httpClient.resolveConfig(eq(TENANT), eq("DIALOG_BSS"))).thenReturn(validConfig());
        when(httpClient.get(eq(TENANT), eq("https://bss-mock.selfcare.io/api"),
                eq("/subscriber/credit/" + CONN), anyMap(), isNull(), eq("dev-key")))
                .thenReturn(null);

        assertNull(provider.fetchCreditLimit(TENANT, CONN));
    }
}