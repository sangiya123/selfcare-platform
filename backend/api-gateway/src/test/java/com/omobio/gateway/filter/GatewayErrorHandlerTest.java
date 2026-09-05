package com.omobio.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omobio.platform.common.web.ApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GatewayErrorHandler.
 *
 * Verifies the ErrorWebExceptionHandler maps every exception type to the
 * correct HTTP status code, error code, and JSON envelope structure.
 */
class GatewayErrorHandlerTest {

    private GatewayErrorHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new GatewayErrorHandler(objectMapper);
    }

    private ServerWebExchange buildExchange() {
        var request = MockServerHttpRequest.get("/api/v1/test")
            .header("X-Tenant-Id", "dialog-lk")
            .header("X-Correlation-Id", "omobio-test-123")
            .build();
        return MockServerWebExchange.from(request);
    }

    // ======================================================================
    // Happy path — no exception
    // ======================================================================

    @Test
    @DisplayName("Handler returns Mono.error when response already committed")
    void committedResponse_returnsError() {
        ServerWebExchange exchange = buildExchange();
        // Simulate already-committed response
        exchange.getResponse().setComplete();

        Mono<Void> result = handler.handle(exchange, new RuntimeException("boom"));
        StepVerifier.create(result)
            .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("boom"))
            .verify();
    }

    // ======================================================================
    // Circuit breaker
    // ======================================================================

    @Test
    @DisplayName("CallNotPermittedException → 503 CIRCUIT_BREAKER_OPEN")
    void circuitBreakerOpen_returns503() {
        ServerWebExchange exchange = buildExchange();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker cb = registry.circuitBreaker("payment-service");
        cb.transitionToOpenState();
        CallNotPermittedException cbe = CallNotPermittedException
            .createCallNotPermittedException(cb);

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, cbe).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"CIRCUIT_BREAKER_OPEN\"");
        assertThat(body).contains("\"retryable\":true");
        assertThat(body).contains("payment-service");
    }

    // ======================================================================
    // ResponseStatusException (downstream 4xx/5xx)
    // ======================================================================

    @Test
    @DisplayName("ResponseStatusException 401 → 401 UNAUTHORIZED")
    void responseStatus_401() {
        ServerWebExchange exchange = buildExchange();
        ResponseStatusException rse = new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Invalid credentials");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, rse).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("ResponseStatusException 403 → 403 FORBIDDEN")
    void responseStatus_403() {
        ServerWebExchange exchange = buildExchange();
        ResponseStatusException rse = new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, rse).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    @DisplayName("ResponseStatusException 404 → 404 NOT_FOUND")
    void responseStatus_404() {
        ServerWebExchange exchange = buildExchange();
        ResponseStatusException rse = new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Resource not found");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, rse).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    @DisplayName("ResponseStatusException 502 → 502 BAD_GATEWAY")
    void responseStatus_502() {
        ServerWebExchange exchange = buildExchange();
        ResponseStatusException rse = new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Upstream error");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, rse).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"BAD_GATEWAY\"");
    }

    // ======================================================================
    // Service not found (no instances)
    // ======================================================================

    @Test
    @DisplayName("NotFoundException from load balancer → 503 SERVICE_NOT_FOUND")
    void serviceNotFound_returns503() {
        ServerWebExchange exchange = buildExchange();
        RuntimeException notFound = new RuntimeException(
            "ServiceInstance has no available instances for: payment-service");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, notFound).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"SERVICE_NOT_FOUND\"");
        assertThat(body).contains("\"retryable\":true");
    }

    // ======================================================================
    // Timeouts
    // ======================================================================

    @Test
    @DisplayName("ReadTimeoutException → 504 GATEWAY_TIMEOUT")
    void readTimeout_returns504() {
        ServerWebExchange exchange = buildExchange();
        RuntimeException timeout = new RuntimeException(
            "ReadTimeoutException: Connection timeout reading response");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, timeout).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"GATEWAY_TIMEOUT\"");
        assertThat(body).contains("\"retryable\":true");
    }

    @Test
    @DisplayName("ConnectTimeoutException → 504 GATEWAY_TIMEOUT")
    void connectTimeout_returns504() {
        ServerWebExchange exchange = buildExchange();
        RuntimeException timeout = new RuntimeException(
            "ConnectTimeoutException connecting to payment-service:8080");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, timeout).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    // ======================================================================
    // Generic internal error
    // ======================================================================

    @Test
    @DisplayName("Unexpected exception → 500 INTERNAL_ERROR")
    void unexpectedException_returns500() {
        ServerWebExchange exchange = buildExchange();
        NullPointerException npe = new NullPointerException("something went wrong");

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, npe).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = response.getBodyAsString().block();
        assertThat(body).contains("\"code\":\"INTERNAL_ERROR\"");
    }

    // ======================================================================
    // Correlation ID propagation
    // ======================================================================

    @Test
    @DisplayName("Correlation ID is echoed back in error response")
    void correlationId_echoedInResponse() {
        ServerWebExchange exchange = buildExchange();

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, new RuntimeException("boom")).block();

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("omobio-test-123");
        assertThat(response.getHeaders().getFirst("X-Tenant-Id")).isEqualTo("dialog-lk");
    }

    @Test
    @DisplayName("Missing correlation ID generates one in error response")
    void missingCorrelationId_generated() {
        var request = MockServerHttpRequest.get("/api/v1/test").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, new RuntimeException("boom")).block();

        String corrId = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(corrId).isNotNull().startsWith("omobio-");
    }

    // ======================================================================
    // JSON envelope structure
    // ======================================================================

    @Test
    @DisplayName("Response body is valid JSON with error envelope")
    void validJsonEnvelope() {
        ServerWebExchange exchange = buildExchange();
        ApiException apiEx = ApiException.withDetails(
            "TEST_ERROR",
            "Test error",
            500,
            "/api/v1/test",
            "2026-09-03T10:00:00Z",
            "corr-1",
            null
        );

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        handler.handle(exchange, apiEx).block();

        String body = response.getBodyAsString().block();
        assertThat(body).startsWith("{\"error\":{");
        assertThat(body).contains("\"code\":\"TEST_ERROR\"");
        assertThat(body).contains("\"message\":\"Test error\"");
        assertThat(body).contains("\"path\":\"/api/v1/test\"");
    }
}
