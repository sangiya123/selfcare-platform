package com.selfcare.platform.common.context;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Canonical request context propagated across every call in the platform.
 *
 * Immutable, industry-neutral, and populated from incoming headers (see {@link #fromHeaders(Map)}).
 * The same context travels through the api-gateway (reactive), the servlet services, and the
 * async/kafka processing chains via {@link RequestContextHolder}, {@link RequestContextWebFilter}
 * and {@link RequestContextTaskDecorator}.
 *
 * <p>Header contract (SDK mirrors these exact names):</p>
 * <ul>
 *   <li>X-Tenant-Id — registered tenant (validated)</li>
 *   <li>X-User-Id — authenticated user</li>
 *   <li>X-Session-Id — session token reference</li>
 *   <li>X-Correlation-Id — request correlation</li>
 *   <li>X-Trace-Id — distributed trace id</li>
 *   <li>X-Channel — MOBILE / WEB / API / KIOSK / IVR / PARTNER</li>
 *   <li>X-Device-Id — originating device</li>
 *   <li>Accept-Language — requested locale (e.g. en, en-SG)</li>
 *   <li>X-Forwarded-For — caller ip</li>
 *   <li>X-Environment — dev / qa / staging / prod</li>
 * </ul>
 */
public record RequestContext(
        String tenantId,
        String userId,
        String userType,
        String sessionId,
        String token,
        String correlationId,
        String traceId,
        String channel,
        String deviceId,
        String ipAddress,
        Locale locale,
        String environment,
        String authMethod) {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String USER_HEADER = "X-User-Id";
    public static final String SESSION_HEADER = "X-Session-Id";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String CHANNEL_HEADER = "X-Channel";
    public static final String DEVICE_HEADER = "X-Device-Id";
    public static final String LOCALE_HEADER = "Accept-Language";
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String ENVIRONMENT_HEADER = "X-Environment";

    public static RequestContext empty() {
        return new RequestContext(
                "UNKNOWN", null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static RequestContext with(String tenantId, String correlationId) {
        return new RequestContext(
                tenantId, null, null, null, null, correlationId, null, null, null, null, null, null, null);
    }

    public static RequestContext fromHeaders(Map<String, String> headers) {
        return new RequestContext(
                value(headers, TENANT_HEADER),
                value(headers, USER_HEADER),
                value(headers, "X-User-Type"),
                value(headers, SESSION_HEADER),
                value(headers, "Authorization"),
                value(headers, CORRELATION_HEADER),
                value(headers, TRACE_HEADER),
                value(headers, CHANNEL_HEADER),
                value(headers, DEVICE_HEADER),
                value(headers, FORWARDED_FOR_HEADER),
                parseLocale(value(headers, LOCALE_HEADER)),
                value(headers, ENVIRONMENT_HEADER),
                null);
    }

    /** Materialize this context as the canonical outgoing header map (SDK and downstream calls). */
    public Map<String, String> toHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (tenantId != null) headers.put(TENANT_HEADER, tenantId);
        if (userId != null) headers.put(USER_HEADER, userId);
        if (userType != null) headers.put("X-User-Type", userType);
        if (sessionId != null) headers.put(SESSION_HEADER, sessionId);
        if (token != null) headers.put("Authorization", token);
        if (correlationId != null) headers.put(CORRELATION_HEADER, correlationId);
        if (traceId != null) headers.put(TRACE_HEADER, traceId);
        if (channel != null) headers.put(CHANNEL_HEADER, channel);
        if (deviceId != null) headers.put(DEVICE_HEADER, deviceId);
        if (ipAddress != null) headers.put(FORWARDED_FOR_HEADER, ipAddress);
        if (locale != null) headers.put(LOCALE_HEADER, locale.toLanguageTag());
        if (environment != null) headers.put(ENVIRONMENT_HEADER, environment);
        if (authMethod != null) headers.put("X-Auth-Method", authMethod);
        return headers;
    }

    private static String value(Map<String, String> headers, String name) {
        String v = headers.get(name);
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    private static Locale parseLocale(String tag) {
        if (tag == null || tag.isBlank()) return null;
        try {
            return Locale.forLanguageTag(tag.split(",")[0].trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}