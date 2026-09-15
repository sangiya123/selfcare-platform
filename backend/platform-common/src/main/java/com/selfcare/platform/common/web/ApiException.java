package com.selfcare.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Standard error envelope for all API responses.
 *
 * @see GlobalExceptionHandler
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiException extends RuntimeException {

    private final String code;
    private final String message;
    private final int status;
    private final String path;
    private final String timestamp;
    private final String correlationId;
    private final Map<String, Object> details;
    private final List<ValidationError> validationErrors;

    /** 2-arg (HttpStatus, String message) — most common call site */
    public ApiException(org.springframework.http.HttpStatus httpStatus, String message) {
        this("HTTP_" + httpStatus.value(), message, httpStatus.value(), null, null, null, null, null);
    }

    /** 3-arg (code, message, status) */
    public ApiException(String code, String message, int status) {
        this(code, message, status, null, null, null, null, null);
    }

    /** 4-arg (code, message, status, path) */
    public ApiException(String code, String message, int status, String path) {
        this(code, message, status, path, null, null, null, null);
    }

    /** 5-arg (code, message, status, path, correlationId) */
    public ApiException(String code, String message, int status, String path, String correlationId) {
        this(code, message, status, path, null, correlationId, null, null);
    }

    private ApiException(String code, String message, int status, String path,
                        String timestamp, String correlationId,
                        Map<String, Object> details,
                        List<ValidationError> validationErrors) {
        super(message);
        this.code = code;
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = timestamp;
        this.correlationId = correlationId;
        this.details = details;
        this.validationErrors = validationErrors;
    }

    // Static factory methods for callers who prefer them

    public static ApiException of(String code, String message, int status) {
        return new ApiException(code, message, status);
    }

    public static ApiException of(String code, String message, int status, String path) {
        return new ApiException(code, message, status, path);
    }

    public static ApiException of(String code, String message, int status, String path, String correlationId) {
        return new ApiException(code, message, status, path, correlationId);
    }

    public static ApiException of(String code, String message) {
        return new ApiException(code, message, 500);
    }

    /**
     * Fully-populated factory — used by global exception handlers that have
     * a path, timestamp, correlationId and structured details to attach.
     */
    public static ApiException withDetails(String code, String message, int status,
                                          String path, String timestamp, String correlationId,
                                          Map<String, Object> details) {
        return new ApiException(code, message, status, path,
                timestamp, correlationId, details, null);
    }
}
