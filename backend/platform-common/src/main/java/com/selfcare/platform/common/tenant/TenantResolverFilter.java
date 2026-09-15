package com.selfcare.platform.common.tenant;

import com.selfcare.platform.common.context.RequestContext;
import com.selfcare.platform.common.context.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
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
 *
 * Servlet-only: reactive apps (e.g. api-gateway / Spring Cloud Gateway) use
 * the TenantRoutingGatewayFilterFactory for tenant resolution.
 */
@Slf4j
@Component
@Order(1) // Run very early — before security, before everything
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TenantResolverFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String DEFAULT_CORRELATION_PREFIX = "selfcare-";

    private final TenantValidator tenantValidator;

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

            RequestContext requestContext = RequestContext.fromHeaders(headersOf(request));
            RequestContextHolder.set(requestContext);

            MDC.put("tenantId", tenantId);
            MDC.put("correlationId", correlationId);
            if (requestContext.userId() != null) {
                MDC.put("userId", requestContext.userId());
            }

            // Log entry for observability
            log.debug("Tenant resolved: tenantId={}, correlationId={}, uri={}",
                    tenantId, correlationId, request.getRequestURI());

            // Validate tenant is registered
            if (!tenantValidator.isTenantActive(tenantId)) {
                log.warn("Unknown or inactive tenant: {}", tenantId);
                response.sendError(HttpStatus.BAD_REQUEST.value(),
                        "Unknown or inactive tenant: " + tenantId);
                return;
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear thread-local to prevent leaks
            TenantContext.clear();
            RequestContextHolder.clear();
            MDC.remove("tenantId");
            MDC.remove("correlationId");
            MDC.remove("userId");
        }
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RequestContext.TENANT_HEADER, headerValue(request, RequestContext.TENANT_HEADER));
        headers.put(RequestContext.USER_HEADER, headerValue(request, RequestContext.USER_HEADER));
        headers.put("X-User-Type", headerValue(request, "X-User-Type"));
        headers.put(RequestContext.SESSION_HEADER, headerValue(request, RequestContext.SESSION_HEADER));
        headers.put("Authorization", headerValue(request, "Authorization"));
        headers.put(RequestContext.CORRELATION_HEADER, request.getHeader(RequestContext.CORRELATION_HEADER) != null
                ? request.getHeader(RequestContext.CORRELATION_HEADER)
                : correlationId());
        headers.put(RequestContext.TRACE_HEADER, headerValue(request, RequestContext.TRACE_HEADER));
        headers.put(RequestContext.CHANNEL_HEADER, headerValue(request, RequestContext.CHANNEL_HEADER));
        headers.put(RequestContext.DEVICE_HEADER, headerValue(request, RequestContext.DEVICE_HEADER));
        headers.put(RequestContext.FORWARDED_FOR_HEADER, headerValue(request, RequestContext.FORWARDED_FOR_HEADER));
        headers.put(RequestContext.LOCALE_HEADER, headerValue(request, RequestContext.LOCALE_HEADER));
        headers.put(RequestContext.ENVIRONMENT_HEADER, headerValue(request, RequestContext.ENVIRONMENT_HEADER));
        return headers;
    }

    private String correlationId() {
        String existing = TenantContext.get().getCorrelationId();
        return existing != null && !existing.isBlank()
                ? existing
                : DEFAULT_CORRELATION_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    private String headerValue(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank() ? value : null;
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
        // Don't filter actuator endpoints, health checks, JWKS/health endpoints,
        // OIDC discovery paths, or error dispatch (avoid masking real errors as 401/400)
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") ||
               path.startsWith("/health/") ||
               path.equals("/") ||
               path.startsWith("/swagger") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/.well-known/") ||
               path.equals("/error");
    }
}
