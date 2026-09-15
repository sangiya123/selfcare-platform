package com.selfcare.hutch.provider;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Hutch-specific API exception. Carries the upstream HTTP status, a Hutch
 * error code, and a structured error payload for debugging and monitoring.
 * Thrown by {@link HutchHttpClient} on non-2xx responses from Hutch BSS
 * and SMSC.
 */
@Getter
public class HutchApiException extends RuntimeException {

    /** HTTP status returned by the Hutch API. */
    private final HttpStatusCode statusCode;

    /**
     * Hutch error code from the response body, e.g.
     * {@code "HUTCH-BSS-404"}, {@code "HUTCH-SMSC-500"}.
     */
    private final String hutchErrorCode;

    /** Structured error payload from the Hutch API response. */
    private final Map<String, Object> errorPayload;

    public HutchApiException(HttpStatusCode statusCode, String hutchErrorCode, String message) {
        this(statusCode, hutchErrorCode, message, (Map<String, Object>) null);
    }

    public HutchApiException(HttpStatusCode statusCode, String hutchErrorCode,
                             String message, Map<String, Object> errorPayload) {
        super(buildMessage(statusCode, hutchErrorCode, message));
        this.statusCode = statusCode;
        this.hutchErrorCode = hutchErrorCode;
        this.errorPayload = errorPayload;
    }

    public HutchApiException(HttpStatusCode statusCode, String hutchErrorCode,
                             String message, Throwable cause) {
        super(buildMessage(statusCode, hutchErrorCode, message), cause);
        this.statusCode = statusCode;
        this.hutchErrorCode = hutchErrorCode;
        this.errorPayload = null;
    }

    private static String buildMessage(HttpStatusCode status, String code, String message) {
        return String.format("Hutch API error [status=%s, code=%s]: %s",
                status != null ? status.value() : "n/a", code, message);
    }

    // ======================
    // Hutch Error Code Constants
    // ======================

    // Authentication
    public static final String HUTCH_AUTH_001 = "HUTCH-AUTH-001"; // Invalid OTP
    public static final String HUTCH_AUTH_002 = "HUTCH-AUTH-002"; // Token expired
    public static final String HUTCH_AUTH_003 = "HUTCH-AUTH-003"; // Rate limit
    public static final String HUTCH_AUTH_004 = "HUTCH-AUTH-004"; // Invalid API key

    // BSS / Balance / Usage
    public static final String HUTCH_BSS_404 = "HUTCH-BSS-404"; // Subscriber not found
    public static final String HUTCH_BSS_403 = "HUTCH-BSS-403"; // Subscriber suspended
    public static final String HUTCH_BSS_500 = "HUTCH-BSS-500"; // BSS upstream failure

    // SMSC
    public static final String HUTCH_SMSC_001 = "HUTCH-SMSC-001"; // Invalid MSISDN
    public static final String HUTCH_SMSC_500 = "HUTCH-SMSC-500"; // SMSC upstream failure

    // Catalog
    public static final String HUTCH_CAT_404 = "HUTCH-CAT-404"; // Product not found
    public static final String HUTCH_CAT_500 = "HUTCH-CAT-500"; // Catalog upstream failure

    // General
    public static final String HUTCH_GEN_500 = "HUTCH-GEN-500"; // Hutch internal failure
    public static final String HUTCH_GEN_503 = "HUTCH-GEN-503"; // Hutch service unavailable

    // Security
    public static final String HUTCH_SEC_001 = "HUTCH-SEC-001"; // Outbound URL blocked by SSRF allow list
}
