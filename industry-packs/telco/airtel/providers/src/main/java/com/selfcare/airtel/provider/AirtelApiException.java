package com.selfcare.airtel.provider;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Airtel-specific API exception. Carries the upstream HTTP status, an
 * Airtel error code, and a structured error payload for debugging and
 * monitoring. Thrown by {@link AirtelHttpClient} on non-2xx responses
 * from Airtel Money Gateway and Airtel BSS.
 */
@Getter
public class AirtelApiException extends RuntimeException {

    /** HTTP status returned by the Airtel API. */
    private final HttpStatusCode statusCode;

    /**
     * Airtel error code from the response body, e.g.
     * {@code "AIRTEL-MGW-001"}, {@code "AIRTEL-BSS-404"}.
     */
    private final String airtelErrorCode;

    /** Structured error payload from the Airtel API response. */
    private final Map<String, Object> errorPayload;

    public AirtelApiException(HttpStatusCode statusCode, String airtelErrorCode, String message) {
        this(statusCode, airtelErrorCode, message, (Map<String, Object>) null);
    }

    public AirtelApiException(HttpStatusCode statusCode, String airtelErrorCode,
                              String message, Map<String, Object> errorPayload) {
        super(buildMessage(statusCode, airtelErrorCode, message));
        this.statusCode = statusCode;
        this.airtelErrorCode = airtelErrorCode;
        this.errorPayload = errorPayload;
    }

    public AirtelApiException(HttpStatusCode statusCode, String airtelErrorCode,
                              String message, Throwable cause) {
        super(buildMessage(statusCode, airtelErrorCode, message), cause);
        this.statusCode = statusCode;
        this.airtelErrorCode = airtelErrorCode;
        this.errorPayload = null;
    }

    private static String buildMessage(HttpStatusCode status, String code, String message) {
        return String.format("Airtel API error [status=%s, code=%s]: %s",
                status != null ? status.value() : "n/a", code, message);
    }

    // ======================
    // Airtel Error Code Constants
    // ======================

    // Authentication
    public static final String AIRTEL_AUTH_001 = "AIRTEL-AUTH-001"; // Invalid OTP
    public static final String AIRTEL_AUTH_002 = "AIRTEL-AUTH-002"; // Token expired
    public static final String AIRTEL_AUTH_003 = "AIRTEL-AUTH-003"; // Invalid client secret
    public static final String AIRTEL_AUTH_004 = "AIRTEL-AUTH-004"; // Rate limit

    // Money Gateway (Airtel Money)
    public static final String AIRTEL_MGW_001 = "AIRTEL-MGW-001"; // Payment declined
    public static final String AIRTEL_MGW_002 = "AIRTEL-MGW-002"; // Insufficient balance
    public static final String AIRTEL_MGW_404 = "AIRTEL-MGW-404"; // Transaction not found
    public static final String AIRTEL_MGW_500 = "AIRTEL-MGW-500"; // Gateway upstream failure

    // BSS / Balance / Usage
    public static final String AIRTEL_BSS_404 = "AIRTEL-BSS-404"; // Subscriber not found
    public static final String AIRTEL_BSS_403 = "AIRTEL-BSS-403"; // Subscriber suspended
    public static final String AIRTEL_BSS_500 = "AIRTEL-BSS-500"; // BSS upstream failure

    // SMSC
    public static final String AIRTEL_SMSC_001 = "AIRTEL-SMSC-001"; // Invalid MSISDN
    public static final String AIRTEL_SMSC_500 = "AIRTEL-SMSC-500"; // SMSC upstream failure

    // Catalog
    public static final String AIRTEL_CAT_404 = "AIRTEL-CAT-404"; // Product not found
    public static final String AIRTEL_CAT_500 = "AIRTEL-CAT-500"; // Catalog upstream failure

    // General
    public static final String AIRTEL_GEN_500 = "AIRTEL-GEN-500"; // Airtel internal failure
    public static final String AIRTEL_GEN_503 = "AIRTEL-GEN-503"; // Airtel service unavailable

    // Security
    public static final String AIRTEL_SEC_001 = "AIRTEL-SEC-001"; // Outbound URL blocked by SSRF allow list
}
