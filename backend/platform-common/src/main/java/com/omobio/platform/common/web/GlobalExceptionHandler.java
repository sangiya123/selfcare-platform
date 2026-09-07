package com.omobio.platform.common.web;

import com.omobio.platform.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler for all services.
 *
 * Converts all known exception types to the standard ApiException envelope.
 * Every error response includes correlationId, path, and timestamp.
 *
 * Add @RestControllerAdvice to your service's package or use component scan.
 *
 * Servlet-only: reactive apps (e.g. api-gateway / Spring Cloud Gateway) use
 * the gateway's reactive WebExceptionHandler instead.
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex, WebRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, null, false);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, WebRequest request) {
        Map<String, Object> details = null;
        if (ex.getResourceType() != null) {
            details = Map.of(
                "resourceType", ex.getResourceType(),
                "resourceId", ex.getResourceId() != null ? ex.getResourceId() : ""
            );
        }
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, details, false);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, null, false);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, null, false);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, null, false);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex, WebRequest request) {
        Map<String, Object> details = null;
        if (ex.getAction() != null) {
            details = Map.of(
                "action", ex.getAction(),
                "target", ex.getTarget() != null ? ex.getTarget() : ""
            );
        }
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request, details, false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage(), request, null, false);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(ServiceUnavailableException ex, WebRequest request) {
        Map<String, Object> details = ex.getProvider() != null
            ? Map.of("provider", ex.getProvider())
            : null;
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage(),
                request, details, ex.isRetryable());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            errors.add(ValidationError.of(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", Map.of(
                "code", "VALIDATION_FAILED",
                "message", "Request validation failed",
                "correlationId", TenantContext.get().getCorrelationId() != null ? TenantContext.get().getCorrelationId() : "",
                "retryable", false,
                "details", errors
            )
        ));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex, WebRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND",
                "No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL(), request, null, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled exception in request {}: {}", extractPath(request), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", request,
                Map.of("type", ex.getClass().getSimpleName()), false);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String code, String message, WebRequest request,
            Map<String, Object> details, boolean retryable) {
        log.warn("API error: code={}, status={}, message={}, path={}, retryable={}",
                code, status.value(), message, extractPath(request), retryable);
        return ResponseEntity.status(status).body(Map.of(
            "error", Map.of(
                "code", code,
                "message", message,
                "correlationId", TenantContext.get().getCorrelationId() != null ? TenantContext.get().getCorrelationId() : "",
                "retryable", retryable,
                "details", details != null ? details : List.of()
            )
        ));
    }

    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        return description != null ? description.replace("uri=", "") : "";
    }
}