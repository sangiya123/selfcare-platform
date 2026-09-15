package com.selfcare.aia.provider;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * AIA-specific API exception. Carries the upstream HTTP status, the
 * AIA error code returned by the insurer's API, and a structured
 * error payload for logging / troubleshooting.
 *
 * Used by {@link AIAHttpClient} to surface detailed failure information
 * to the provider methods.
 */
@Getter
public class AIAApiException extends RuntimeException {

    /** HTTP status returned by the AIA API. */
    private final HttpStatusCode statusCode;

    /** AIA error code, e.g. AIA-AUTH-001, AIA-CLM-404, AIA-POL-403. */
    private final String aiaErrorCode;

    /** Structured error payload returned by the AIA API. */
    private final Map<String, Object> errorPayload;

    public AIAApiException(HttpStatusCode statusCode, String aiaErrorCode, String message) {
        this(statusCode, aiaErrorCode, message, (Map<String, Object>) null);
    }

    public AIAApiException(HttpStatusCode statusCode, String aiaErrorCode, String message,
                           Map<String, Object> errorPayload) {
        super(buildMessage(statusCode, aiaErrorCode, message));
        this.statusCode = statusCode;
        this.aiaErrorCode = aiaErrorCode;
        this.errorPayload = errorPayload;
    }

    public AIAApiException(HttpStatusCode statusCode, String aiaErrorCode, String message, Throwable cause) {
        super(buildMessage(statusCode, aiaErrorCode, message), cause);
        this.statusCode = statusCode;
        this.aiaErrorCode = aiaErrorCode;
        this.errorPayload = null;
    }

    private static String buildMessage(HttpStatusCode status, String code, String message) {
        return String.format("AIA API error [status=%s, code=%s]: %s",
                status != null ? status.value() : "n/a", code, message);
    }

    // ======================
    // AIA Error Code Constants
    // ======================

    // Authentication / OAuth
    public static final String AIA_AUTH_001 = "AIA-AUTH-001"; // Invalid client credentials
    public static final String AIA_AUTH_002 = "AIA-AUTH-002"; // Token expired
    public static final String AIA_AUTH_003 = "AIA-AUTH-003"; // Insufficient scope

    // Policy
    public static final String AIA_POL_404 = "AIA-POL-404"; // Policy not found
    public static final String AIA_POL_403 = "AIA-POL-403"; // Forbidden access to policy
    public static final String AIA_POL_409 = "AIA-POL-409"; // Policy state conflict

    // Claims
    public static final String AIA_CLM_400 = "AIA-CLM-400"; // Invalid claim payload
    public static final String AIA_CLM_404 = "AIA-CLM-404"; // Claim not found
    public static final String AIA_CLM_409 = "AIA-CLM-409"; // Duplicate claim

    // Premium
    public static final String AIA_PRM_402 = "AIA-PRM-402"; // Payment declined
    public static final String AIA_PRM_404 = "AIA-PRM-404"; // Premium record not found

    // Beneficiary
    public static final String AIA_BEN_400 = "AIA-BEN-400"; // Invalid beneficiary data
    public static final String AIA_BEN_409 = "AIA-BEN-409"; // Allocation exceeds 100%

    // General
    public static final String AIA_GEN_500 = "AIA-GEN-500"; // Insurer upstream failure
    public static final String AIA_GEN_503 = "AIA-GEN-503"; // Insurer unavailable

    // Security
    public static final String AIA_SEC_001 = "AIA-SEC-001"; // Outbound URL blocked by SSRF allow list
}
