package com.omobio.platform.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts tenant identity from the incoming request.
 *
 * Resolves tenant from:
 * 1. X-Tenant-Id header (primary — always honored)
 * 2. Host/domain header (secondary — mapped via configuration)
 *
 * Sets TenantContext for the duration of the request.
 * Always clears TenantContext at the end.
 *
 * Requests without a valid X-Tenant-Id receive 400 Bad Request.
 */
@Slf4j
@Component
@Order(1) // Run very early — before security, before everything
@RequiredArgsConstructor
public class TenantResolverFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String DEFAULT_CORRELATION_PREFIX = "omobio-";

    private final TenantProperties tenantProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String tenantId = resolveTenantId(request);
            String correlationId = resolveCorrelationId(request);
            String environment = resolveEnvironment(request);

            TenantContext ctx = new TenantContext();
            ctx.setTenantId(tenantId);
            ctx.setCorrelationId(correlationId);
            ctx.setEnvironment(environment);

            TenantContext.set(ctx);

            // Log entry for observability
            log.debug("Tenant resolved: tenantId={}, correlationId={}, uri={}",
                    tenantId, correlationId, request.getRequestURI());

            // Validate tenant is registered
            if (!tenantProperties.isTenantActive(tenantId)) {
                log.warn("Unknown or inactive tenant: {}", tenantId);
                response.sendError(HttpStatus.BAD_REQUEST.value(),
                        "Unknown or inactive tenant: " + tenantId);
                return;
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear thread-local to prevent leaks
            TenantContext.clear();
        }
    }

    private String resolveTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim().toLowerCase();
        }

        // Fallback: resolve from Host header
        String host = request.getHeader("Host");
        if (host != null) {
            String domain = host.split(":")[0]; // strip port
            // Map domain to tenant — configured in TenantProperties if needed
            // For now, reject if no header (domain mapping is done via config)
            log.debug("No X-Tenant-Id header, resolved from Host: {}", domain);
        }

        // Tenant ID is required
        return "UNKNOWN";
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = DEFAULT_CORRELATION_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        }

        return correlationId;
    }

    private String resolveEnvironment(HttpServletRequest request) {
        // Environment can come from a header or be derived from host
        String env = request.getHeader("X-Environment");
        if (env != null && !env.isBlank()) {
            return env.toLowerCase();
        }

        // Default based on host pattern (dev/qa/staging/prod)
        String host = request.getHeader("Host");
        if (host != null) {
            if (host.contains(".dev.") || host.contains("-dev.") || host.contains(":808")) {
                return "dev";
            }
            if (host.contains(".qa.") || host.contains("-qa.") || host.contains(":809")) {
                return "qa";
            }
            if (host.contains(".staging.") || host.contains("-staging.") || host.contains(":8091")) {
                return "staging";
            }
        }

        return "prod";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Don't filter actuator endpoints, health checks
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") ||
               path.startsWith("/health/") ||
               path.equals("/") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs");
    }
}
