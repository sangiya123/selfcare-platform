package com.omobio.platform.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

/**
 * Centralised telemetry emitter for the platform.
 * All services emit metrics through this service so that:
 * - Metric names are consistent across the platform
 * - Labels (dimensions) follow the ADR-021 convention
 * - High-cardinality labels are bounded
 *
 * @see ADR-021: Observability Stack
 */
@Service
public class TelemetryService {

    private final MeterRegistry registry;

    public TelemetryService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Emit a provider call metric.
     * Counter: provider_call_total{provider, status, outcome}
     */
    public void emitProviderCall(
            String providerId,
            String status,
            String errorClass,
            int retryCount,
            String circuitState,
            long latencyMs) {

        Counter.builder("provider_call_total")
            .description("Total provider calls")
            .tag("provider", providerId)
            .tag("status", status)
            .tag("error_class", errorClass != null ? errorClass : "none")
            .tag("circuit_state", circuitState)
            .register(registry)
            .increment();
    }

    /**
     * Emit a generic counter metric.
     */
    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
            .register(registry)
            .increment();
    }

    /**
     * Emit a generic timer metric.
     */
    public void recordDuration(String name, long durationMs, String... tags) {
        Timer.builder(name)
            .register(registry)
            .record(java.time.Duration.ofMillis(durationMs));
    }
}
