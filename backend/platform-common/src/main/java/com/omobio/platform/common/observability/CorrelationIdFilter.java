package com.omobio.platform.common.observability;

import com.omobio.platform.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that propagates the correlation ID from the request to:
 * 1. The TenantContext (for logging)
 * 2. The response headers (for client observability)
 * 3. MDC for log correlation
 *
 * Also generates a new correlation ID if not present.
 *
 * Runs after TenantResolverFilter.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // After tenant resolver
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String CORRELATION_ID_LOGGER_PREFIX = "omobio-";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CORRELATION_ID_LOGGER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            // Set MDC for log correlation
            org.slf4j.MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Update TenantContext if already set
            if (TenantContext.get() != null && TenantContext.get().getTenantId() != null) {
                TenantContext.get().setCorrelationId(correlationId);
            }

            // Propagate to response
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // Add tenant header to response
            String tenantId = TenantContext.get().getTenantId();
            if (tenantId != null) {
                response.setHeader(TenantResolverFilter.TENANT_HEADER, tenantId);
            }

            filterChain.doFilter(request, response);

        } finally {
            org.slf4j.MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}