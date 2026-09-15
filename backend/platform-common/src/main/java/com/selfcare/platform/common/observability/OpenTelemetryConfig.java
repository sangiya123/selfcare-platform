package com.selfcare.platform.common.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration for distributed tracing and metrics.
 *
 * Provides:
 * - Tracer for creating spans
 * - Resource attributes (service.name, service.version)
 * - OTLP gRPC exporter to the observability backend
 *
 * Configuration:
 *   selfcare.otel.endpoint=http://otel-collector:4317
 *   selfcare.otel.service.name=api-gateway
 *   selfcare.otel.enabled=true
 */
@Slf4j
@Configuration
public class OpenTelemetryConfig {

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");
    private static final AttributeKey<String> DEPLOYMENT_ENV = AttributeKey.stringKey("deployment.environment");

    @Value("${selfcare.otel.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${selfcare.otel.service.name:unknown-service}")
    private String serviceName;

    @Value("${selfcare.otel.enabled:true}")
    private boolean enabled;

    @Bean
    public OpenTelemetry openTelemetry() {
        if (!enabled) {
            log.info("OpenTelemetry disabled");
            return OpenTelemetry.noop();
        }

        log.info("Configuring OpenTelemetry for service={}, endpoint={}", serviceName, otlpEndpoint);

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        SERVICE_NAME, serviceName,
                        SERVICE_VERSION, "1.0.0",
                        DEPLOYMENT_ENV, System.getenv().getOrDefault("ENVIRONMENT", "dev")
                ))
        );

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0");
    }
}
