package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.PaymentRequest;
import com.selfcare.platform.common.adapter.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DialogPaymentProviderTest {

    @Mock
    private DialogHttpClient httpClient;

    private DialogPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DialogPaymentProvider(httpClient);
    }

    @Test
    @DisplayName("getAdapterId returns dialog-lk")
    void adapterId() {
        assertEquals("dialog-lk", provider.getAdapterId());
    }

    @Test
    @DisplayName("execute returns FAILED for unsupported transaction type")
    void unsupportedType() {
        PaymentRequest request = PaymentRequest.builder()
                .transactionId("tx-001")
                .tenantId("dialog-lk")
                .transactionType("UNSUPPORTED_TYPE")
                .amount(BigDecimal.TEN)
                .currency("LKR")
                .build();

        PaymentResult result = provider.execute(request, "https://example.com");

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getFailureReason().contains("Unsupported transaction type"));
    }

    @Test
    @DisplayName("DialogApiException 3-arg constructor compiles and sets fields")
    void apiExceptionCompact() {
        var ex = new DialogApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                DialogApiException.DIALOG_AUTH_001,
                "Invalid credentials");

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("DIALOG-AUTH-001", ex.getDialogErrorCode());
        assertNull(ex.getErrorPayload());
    }
}