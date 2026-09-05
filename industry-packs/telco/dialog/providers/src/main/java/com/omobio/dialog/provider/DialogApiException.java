package com.omobio.dialog.provider;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Dialog-specific API exception.
 *
 * Carries the upstream HTTP status, a Dialog error code, and a structured
 * error payload for debugging and monitoring. Thrown by {@link DialogHttpClient}
 * on non-2xx responses from Dialog's APIs (MIFE, BSS, SMSC, VAS).
 */
@Getter
public class DialogApiException extends RuntimeException {

    /** HTTP status returned by the Dialog API. */
    private final HttpStatusCode statusCode;

    /**
     * Dialog error code from the response body, e.g.
     * {@code "DIALOG-AUTH-001"}, {@code "DIALOG-BSS-404"}.
     * {@code null} when the response had no body or no code field.
     */
    private final String dialogErrorCode;

    /**
     * Structured error payload from the Dialog API response.
     * May contain {@code code}, {@code message}, {@code details}, etc.
     */
    private final Map<String, Object> errorPayload;

    public DialogApiException(HttpStatusCode statusCode, String dialogErrorCode, String message) {
        this(statusCode, dialogErrorCode, message, null);
    }

    public DialogApiException(HttpStatusCode statusCode, String dialogErrorCode,
                             String message, Map<String, Object> errorPayload) {
        super(buildMessage(statusCode, dialogErrorCode, message));
        this.statusCode = statusCode;
        this.dialogErrorCode = dialogErrorCode;
        this.errorPayload = errorPayload;
    }

    public DialogApiException(HttpStatusCode statusCode, String dialogErrorCode,
                             String message, Throwable cause) {
        super(buildMessage(statusCode, dialogErrorCode, message), cause);
        this.statusCode = statusCode;
        this.dialogErrorCode = dialogErrorCode;
        this.errorPayload = null;
    }

    private static String buildMessage(HttpStatusCode status, String code, String message) {
        return String.format("Dialog API error [status=%s, code=%s]: %s",
                status != null ? status.value() : "n/a", code, message);
    }

    // ======================
    // Dialog Error Code Constants
    // ======================

    // Authentication / MIFE
    public static final String DIALOG_AUTH_001 = "DIALOG-AUTH-001"; // Invalid credentials
    public static final String DIALOG_AUTH_002 = "DIALOG-AUTH-002"; // Token expired
    public static final String DIALOG_AUTH_003 = "DIALOG-AUTH-003"; // OTP expired / invalid
    public static final String DIALOG_AUTH_004 = "DIALOG-AUTH-004"; // Rate limit exceeded
    public static final String DIALOG_AUTH_005 = "DIALOG-AUTH-005"; // Account locked

    // BSS / Balance / Usage
    public static final String DIALOG_BSS_404 = "DIALOG-BSS-404"; // Subscriber not found
    public static final String DIALOG_BSS_403 = "DIALOG-BSS-403"; // Subscriber suspended
    public static final String DIALOG_BSS_500 = "DIALOG-BSS-500"; // BSS upstream failure

    // SMSC / Notifications
    public static final String DIALOG_SMSC_001 = "DIALOG-SMSC-001"; // Invalid MSISDN
    public static final String DIALOG_SMSC_002 = "DIALOG-SMSC-002"; // Sender ID not permitted
    public static final String DIALOG_SMSC_500 = "DIALOG-SMSC-500"; // SMSC upstream failure

    // VAS / Catalog
    public static final String DIALOG_VAS_404 = "DIALOG-VAS-404"; // Product not found
    public static final String DIALOG_VAS_500 = "DIALOG-VAS-500"; // VAS upstream failure

    // General / cross-cutting
    public static final String DIALOG_GEN_500 = "DIALOG-GEN-500"; // Internal failure
    public static final String DIALOG_GEN_503 = "DIALOG-GEN-503"; // Service unavailable
}
