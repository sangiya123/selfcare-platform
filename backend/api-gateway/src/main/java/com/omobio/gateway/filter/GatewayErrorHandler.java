package com.omobio.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.platform.common.web.ApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Global reactive exception handler for the API Gateway.
 *
 * <p>Spring Boot 4.x / Spring Framework 7.x removed {@code ErrorWebExceptionHandler}
 * in favour of {@link WebExceptionHandler} (in {@code spring-web}) and
 * {@link ErrorAttributes} (in {@code spring-boot}). This class uses the
 * modern approach: a {@link WebExceptionHandler} that produces a platform-standard
 * {@link ApiException} error envelope:</p>
 *
 * <pre>{@code
 * {
 *   "error": {
 *     "code": "SERVICE_UNAVAILABLE",
 *     "message": "Downstream service temporarily unavailable",
 *     "status": 503,
 *     "path": "/api/v1/payments",
 *     "timestamp": "2026-09-03T15:42:00Z",
 *     "correlationId": "omobio-a1b2c3d4",
 *     "details": { "retryable": true, "service": "payment-service" }
 *   }
 * }
 * }</pre>
 *
 * @see ApiException
 * @see WebExceptionHandler
 */
@Slf4j
@Component
@Order(-2) // Run before Spring's default WebFlux exception handler
public class GatewayErrorHandler implements WebExceptionHandler {

    /** Exchange attribute key Spring WebFlux uses to store the correlation ID. */
    private static final String CORRELATION_ID_ATTR = "correlationIdAttributeKey";

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Prevent double-write if response is already committed
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().clear();

        ApiException envelope = buildEnvelope(exchange, ex);

        // Set response status
        response.setStatusCode(HttpStatusCode.valueOf(envelope.getStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Echo back observability headers even on error
        echoObservabilityHeaders(exchange, response);

        // Log with full context
        log.warn(
                "Gateway error [{}] {} {} → {} {} | correlationId={}",
                envelope.getCode(),
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                envelope.getStatus(),
                envelope.getMessage(),
                envelope.getCorrelationId(),
                ex
        );

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of("error", envelope));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise ApiException", e);
            body = ("{\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":"
                    + "\"Failed to serialise error response\",\"status\":500}}").getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    // -------------------------------------------------------------------------
    // Envelope construction
    // -------------------------------------------------------------------------

    private ApiException buildEnvelope(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();
        String correlationId = resolveCorrelationId(exchange);
        Instant timestamp = Instant.now();

        if (ex instanceof CallNotPermittedException cbe) {
            return ApiException.withDetails(
                    "CIRCUIT_BREAKER_OPEN",
                    "Service temporarily unavailable due to circuit breaker open",
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    path,
                    timestamp.toString(),
                    correlationId,
                    Map.of(
                            "retryable", true,
                            "circuitBreaker", cbe.getCausingCircuitBreakerName()
                    )
            );
        }

        if (ex instanceof ResponseStatusException rse) {
            int status = rse.getStatusCode() != null ? rse.getStatusCode().value() : 502;
            String code = mapStatusToCode(status, ex);
            return ApiException.withDetails(
                    code,
                    rse.getReason() != null ? rse.getReason() : code,
                    status,
                    path,
                    timestamp.toString(),
                    correlationId,
                    null
            );
        }

        if (isServiceNotFound(ex)) {
            return ApiException.withDetails(
                    "SERVICE_NOT_FOUND",
                    "Downstream service not found or no healthy instances available",
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    path,
                    timestamp.toString(),
                    correlationId,
                    Map.of("retryable", true)
            );
        }

        if (isTimeoutException(ex)) {
            return ApiException.withDetails(
                    "GATEWAY_TIMEOUT",
                    "Downstream service did not respond in time",
                    HttpStatus.GATEWAY_TIMEOUT.value(),
                    path,
                    timestamp.toString(),
                    correlationId,
                    Map.of("retryable", true)
            );
        }

        String className = ex.getClass().getName();
        if (className.contains("NotFoundException") || className.contains("ServiceUnavailable")) {
            return ApiException.withDetails(
                    "SERVICE_UNAVAILABLE",
                    "Downstream service unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    path,
                    timestamp.toString(),
                    correlationId,
                    Map.of("retryable", true)
            );
        }

        return ApiException.withDetails(
                "INTERNAL_ERROR",
                "An unexpected error occurred processing the request",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                path,
                timestamp.toString(),
                correlationId,
                null
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveCorrelationId(ServerWebExchange exchange) {
        Object attr = exchange.getAttribute(CORRELATION_ID_ATTR);
        if (attr instanceof String cid && !cid.isBlank()) {
            return cid;
        }

        String header = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        if (header != null && !header.isBlank()) {
            return header;
        }

        return "omobio-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private void echoObservabilityHeaders(ServerWebExchange exchange, ServerHttpResponse response) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        if (correlationId != null) {
            response.getHeaders().set("X-Correlation-Id", correlationId);
        }
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (tenantId != null) {
            response.getHeaders().set("X-Tenant-Id", tenantId);
        }
        String configVersion = exchange.getRequest().getHeaders().getFirst("X-Config-Version");
        if (configVersion != null) {
            response.getHeaders().set("X-Config-Version", configVersion);
        }
    }

    private boolean isServiceNotFound(Throwable ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String className = ex.getClass().getName().toLowerCase();
        return msg.contains("not found") || msg.contains("no instances")
                || msg.contains("service instance") || className.contains("notfoundexception")
                || className.contains("servicediscovery");
    }

    private boolean isTimeoutException(Throwable ex) {
        String className = ex.getClass().getName().toLowerCase();
        return className.contains("timeout")
                || className.contains("timelimitexceeded")
                || (ex.getMessage() != null
                && (ex.getMessage().contains("timeout")
                || ex.getMessage().contains("timed out")));
    }

    private String mapStatusToCode(int status, Throwable ex) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            case 502 -> "BAD_GATEWAY";
            case 503 -> "SERVICE_UNAVAILABLE";
            case 504 -> "GATEWAY_TIMEOUT";
            default -> "DOWNSTREAM_ERROR";
        };
    }
}
