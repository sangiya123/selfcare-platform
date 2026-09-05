package com.omobio.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Per-route gateway filter that enforces and propagates tenant identity
 * across the OMOBIO platform.
 *
 * <p>On every matched request this filter:
 * <ol>
 *   <li>Reads the {@code X-Tenant-Id} request header and validates its format
 *       (non-empty, alphanumeric + dash, max 64 chars)</li>
 *   <li>Strips any client-supplied {@code X-User-Id} and {@code X-Session-Id}
 *       headers — user identity is set exclusively by the gateway from the JWT,
 *       never trusted from the client</li>
 *   <li>Adds / overwrites standard headers forwarded to downstream services:
 *       {@code X-Tenant-Id}, {@code X-Correlation-Id}, {@code X-Environment}</li>
 *   <li>Sets {@code X-Forwarded-For} with the real client IP (first-hop from
 *       the reactive request)</li>
 * </ol>
 *
 * <p>Validation failures result in a 400 response; the request is NOT proxied.
 *
 * <p>Usage in application.yml:
 * <pre>
 * filters:
 *   - name: TenantRouting
 * </pre>
 *
 * @see TenantKeyResolver
 * @see JwtAuthenticationHeaderRelay
 */
@Slf4j
@Component
public class TenantRoutingGatewayFilterFactory
        extends AbstractGatewayFilterFactory<TenantRoutingGatewayFilterFactory.Config> {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String ENVIRONMENT_HEADER = "X-Environment";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** Single-value header names that must never be trusted from the client. */
    private static final List<String> STRIPPED_HEADERS = List.of(
            "X-User-Id",
            "X-User-Scopes",
            "X-Session-Id"
    );

    /** Regex: alphanumeric + dash, 1–64 chars. */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    /** Correlation ID prefix for log traceability. */
    private static final String CORRELATION_ID_PREFIX = "omobio-";

    public TenantRoutingGatewayFilterFactory() {
        super(Config.class);
    }

    /**
     * Creates the tenant-routing filter.  Configuration object is a simple
     * holder — all behaviour is fixed for every route.
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // ----------------------------------------------------------------
            // 1. Read and validate X-Tenant-Id
            // ----------------------------------------------------------------
            String tenantId = request.getHeaders().getFirst(TENANT_ID_HEADER);
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Missing X-Tenant-Id header on request {} {}",
                        request.getMethod(), request.getPath());
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
            tenantId = tenantId.trim();
            if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
                log.warn("Invalid X-Tenant-Id format '{}' on request {} {}",
                        tenantId, request.getMethod(), request.getPath());
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            // ----------------------------------------------------------------
            // 2. Correlation ID — generate if absent
            // ----------------------------------------------------------------
            String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = CORRELATION_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8);
            }

            // ----------------------------------------------------------------
            // 3. Client IP for X-Forwarded-For
            // ----------------------------------------------------------------
            String clientIp = resolveClientIp(request);

            // ----------------------------------------------------------------
            // 4. Build mutated request with all tenant + observability headers
            // ----------------------------------------------------------------
            // Compute X-Forwarded-For (chain-aware — append to existing value)
            String existingFwd = request.getHeaders().getFirst(FORWARDED_FOR_HEADER);
            String forwardedFor = (existingFwd != null && !existingFwd.isBlank())
                    ? existingFwd + ", " + clientIp
                    : clientIp;

            ServerHttpRequest mutated = request.mutate()

                    // Set / overwrite tenant identity (immutable — no client trust)
                    .header(TENANT_ID_HEADER, tenantId)
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .header(ENVIRONMENT_HEADER, resolveEnvironment(exchange))
                    .header(FORWARDED_FOR_HEADER, forwardedFor)

                    // Strip client-supplied identity headers (gateway sets these)
                    .headers(headers -> STRIPPED_HEADERS.forEach(headers::remove))

                    .build();

            log.debug("TenantRouting: tenant={} correlation={} uri={}",
                    tenantId, correlationId, request.getPath());

            return chain.filter(exchange.mutate().request(mutated).build());
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the real client IP, honouring de-facto proxy headers in order
     * of preference: {@code X-Forwarded-For}, {@code X-Real-IP}, direct remote
     * address.
     */
    private String resolveClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        // Trust first known proxy hop if present
        String xFwd = headers.getFirst("X-Forwarded-For");
        if (xFwd != null && !xFwd.isBlank()) {
            // Take the left-most (original client) IP before any comma
            return xFwd.split(",")[0].trim();
        }

        String xReal = headers.getFirst("X-Real-IP");
        if (xReal != null && !xReal.isBlank()) {
            return xReal.trim();
        }

        // Fallback: remote address from the reactive request
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Resolves the target deployment environment from the
     * {@code OMOBIO_ENV} environment variable falling back to the
     * {@code Host} header or a default of {@code dev}.
     */
    private String resolveEnvironment(org.springframework.web.server.ServerWebExchange exchange) {
        String env = System.getenv("OMOBIO_ENV");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        String host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
        if (host != null && !host.isBlank()) {
            // Strip port number if present
            int colon = host.lastIndexOf(':');
            String hostname = colon > 0 ? host.substring(0, colon) : host;
            // Detect environment from hostname patterns (e.g. stg-api.omobio.com)
            if (hostname.contains("-stg") || hostname.startsWith("stg-")) {
                return "stg";
            }
            if (hostname.contains("-reg") || hostname.startsWith("reg-")) {
                return "reg";
            }
            if (hostname.contains("-prod") || hostname.startsWith("prod-")) {
                return "prod";
            }
        }

        return "dev";
    }

    /**
     * Marker configuration class consumed by the filter factory.
     * All routing behaviour is uniform across routes so this is a no-op
     * holder.
     */
    public static class Config {
        // No per-route configuration required — all behaviour is fixed.
    }
}
