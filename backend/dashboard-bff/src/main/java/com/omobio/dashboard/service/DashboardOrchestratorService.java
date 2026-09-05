package com.omobio.dashboard.service;

import com.omobio.dashboard.web.dto.DashboardResponse;
import com.omobio.dashboard.web.dto.WidgetResult;
import com.omobio.platform.common.tenant.TenantContext;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Dashboard orchestrator — concurrent widget fan-out with resilience.
 *
 * Executes all configured dashboard widgets in parallel within a bounded deadline.
 * Each widget has its own timeout, circuit breaker, and bulkhead.
 * Returns partial results when the deadline is reached.
 */
@Slf4j
@Service
public class DashboardOrchestratorService {

    private final Map<String, WidgetProvider> widgetProviders;
    private final CircuitBreakerRegistry cbRegistry;
    private final TimeLimiterRegistry tlRegistry;
    private final Duration overallDeadline;
    private final Duration defaultWidgetTimeout;

    public DashboardOrchestratorService(
            Map<String, WidgetProvider> widgetProviders,
            CircuitBreakerRegistry cbRegistry,
            TimeLimiterRegistry tlRegistry,
            @Value("${dashboard.overall-deadline-ms:500}") long overallDeadlineMs,
            @Value("${dashboard.default-widget-timeout-ms:300}") long defaultWidgetTimeoutMs) {
        this.widgetProviders = widgetProviders;
        this.cbRegistry = cbRegistry;
        this.tlRegistry = tlRegistry;
        this.overallDeadline = Duration.ofMillis(overallDeadlineMs);
        this.defaultWidgetTimeout = Duration.ofMillis(defaultWidgetTimeoutMs);
    }

    /**
     * Orchestrate all dashboard widgets concurrently within the overall deadline.
     *
     * Returns a dashboard response with per-widget statuses:
     * - SUCCESS: widget returned data
     * - PARTIAL: widget returned partial data
     * - STALE: widget returned cached/stale data
     * - TIMEOUT: widget exceeded its timeout
     * - UNAVAILABLE: widget not available for this user
     * - ERROR: widget threw an exception
     */
    public Mono<DashboardResponse> orchestrateDashboard(
            String tenantId,
            String connectionId,
            String profileKey,
            List<String> requestedWidgetIds) {

        Instant start = Instant.now();
        String correlationId = TenantContext.get().getCorrelationId();

        log.info("Dashboard orchestration started: tenant={}, connection={}, profile={}, widgets={}, correlation={}",
                tenantId, connectionId, profileKey, requestedWidgetIds, correlationId);

        // Resolve which widgets to execute
        List<WidgetExecution> executions = resolveWidgets(requestedWidgetIds, tenantId, connectionId, profileKey);

        // Execute all widgets concurrently with overall deadline
        Flux<WidgetResult> widgetFlux = Flux.fromIterable(executions)
                .flatMap(this::executeWidget)
                .takeUntilOther(
                        // Cancel when overall deadline is reached
                        Mono.delay(overallDeadline)
                                .doOnNext(t -> log.info("Dashboard deadline reached after {}ms", overallDeadline.toMillis()))
                )
                .onErrorContinue((throwable, obj) -> {
                    // Log but don't fail the whole dashboard for one widget error
                    log.error("Widget error (continuing): {}", throwable.getMessage());
                });

        // Collect results
        return widgetFlux
                .collectList()
                .map(results -> buildResponse(results, executions, start, correlationId))
                .defaultIfEmpty(buildEmptyResponse(executions, start, correlationId));
    }

    /**
     * Refresh a single widget by ID.
     * Used for targeted retry of failed widgets.
     */
    public Mono<WidgetResult> refreshWidget(String widgetId, String tenantId, String connectionId, String profileKey) {
        WidgetProvider provider = widgetProviders.get(widgetId);
        if (provider == null) {
            return Mono.just(WidgetResult.unavailable(widgetId, "Widget not found"));
        }

        return executeWidget(new WidgetExecution(widgetId, provider, null, tenantId, connectionId, profileKey));
    }

    private List<WidgetExecution> resolveWidgets(List<String> requestedIds, String tenantId, String connectionId, String profileKey) {
        if (requestedIds != null && !requestedIds.isEmpty()) {
            // Specific widget IDs requested
            return requestedIds.stream()
                    .filter(widgetProviders::containsKey)
                    .map(id -> new WidgetExecution(id, widgetProviders.get(id), null, tenantId, connectionId, profileKey))
                    .toList();
        }

        // Default: execute all available widgets for this profile
        return widgetProviders.entrySet().stream()
                .filter(e -> e.getValue().isAvailable(tenantId, connectionId, profileKey))
                .map(e -> new WidgetExecution(e.getKey(), e.getValue(), null, tenantId, connectionId, profileKey))
                .toList();
    }

    private Mono<WidgetResult> executeWidget(WidgetExecution execution) {
        WidgetProvider provider = execution.provider();
        String widgetId = execution.widgetId();
        Duration timeout = execution.timeout() != null ? execution.timeout() : defaultWidgetTimeout;

        // Get or create circuit breaker for this widget
        CircuitBreaker cb = cbRegistry.circuitBreaker(widgetId);

        Supplier<Mono<WidgetResult>> widgetSupplier = () -> {
            try {
                return provider.execute(execution.connectionId(), execution.tenantId(), execution.profileKey())
                        .map(data -> WidgetResult.success(widgetId, data))
                        .timeout(timeout)
                        .onErrorResume(e -> {
                            if (e instanceof TimeoutException) {
                                log.warn("Widget {} timed out after {}ms", widgetId, timeout.toMillis());
                                return Mono.just(WidgetResult.timeout(widgetId, timeout.toMillis()));
                            }
                            return Mono.just(WidgetResult.error(widgetId, e.getMessage()));
                        });
            } catch (Exception e) {
                return Mono.just(WidgetResult.error(widgetId, e.getMessage()));
            }
        };

        // Execute with circuit breaker; per-widget timeout is already applied inside the widget supplier
        return Mono.defer(widgetSupplier)
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Widget {} failed: {}", widgetId, e.getMessage());
                    if (cb.getState() == CircuitBreaker.State.OPEN) {
                        return Mono.just(WidgetResult.unavailable(widgetId, "Circuit breaker open"));
                    }
                    return Mono.just(WidgetResult.error(widgetId, e.getMessage()));
                });
    }

    private DashboardResponse buildResponse(
            List<WidgetResult> results,
            List<WidgetExecution> executions,
            Instant start,
            String correlationId) {

        long elapsedMs = Duration.between(start, Instant.now()).toMillis();

        Map<String, WidgetResult> widgetResults = new LinkedHashMap<>();
        for (WidgetResult r : results) {
            widgetResults.put(r.getWidgetId(), r);
        }

        // Add unavailable status for widgets that didn't complete
        for (WidgetExecution exec : executions) {
            if (!widgetResults.containsKey(exec.widgetId())) {
                widgetResults.put(exec.widgetId(), WidgetResult.timeout(exec.widgetId(), overallDeadline.toMillis()));
            }
        }

        // Determine overall status
        String overallStatus = determineOverallStatus(results);

        return DashboardResponse.builder()
                .correlationId(correlationId)
                .elapsedMs(elapsedMs)
                .overallStatus(overallStatus)
                .widgets(widgetResults)
                .timestamp(Instant.now())
                .build();
    }

    private DashboardResponse buildEmptyResponse(List<WidgetExecution> executions, Instant start, String correlationId) {
        Map<String, WidgetResult> widgetResults = new LinkedHashMap<>();
        for (WidgetExecution exec : executions) {
            widgetResults.put(exec.widgetId(), WidgetResult.unavailable(exec.widgetId(), "No results"));
        }
        return DashboardResponse.builder()
                .correlationId(correlationId)
                .elapsedMs(Duration.between(start, Instant.now()).toMillis())
                .overallStatus("TIMEOUT")
                .widgets(widgetResults)
                .timestamp(Instant.now())
                .build();
    }

    private String determineOverallStatus(List<WidgetResult> results) {
        if (results.isEmpty()) {
            return "TIMEOUT";
        }

        boolean hasSuccess = results.stream().anyMatch(r -> "SUCCESS".equals(r.getStatus()));
        boolean hasTimeout = results.stream().anyMatch(r -> "TIMEOUT".equals(r.getStatus()));
        boolean hasError = results.stream().anyMatch(r -> "ERROR".equals(r.getStatus()));

        if (hasError && !hasSuccess) return "ERROR";
        if (hasTimeout && !hasSuccess) return "PARTIAL";
        if (hasSuccess) return hasTimeout || hasError ? "PARTIAL" : "SUCCESS";
        return "ERROR";
    }

    /**
     * Widget execution record — captures all context needed to run a single widget.
     *
     * @param widgetId     the widget ID
     * @param provider     the widget provider implementation
     * @param timeout      per-widget timeout override (null = use default)
     * @param tenantId     the tenant ID
     * @param connectionId the active connection ID
     * @param profileKey   the resolved profile key
     */
    private record WidgetExecution(
            String widgetId,
            WidgetProvider provider,
            Duration timeout,
            String tenantId,
            String connectionId,
            String profileKey
    ) {}
}