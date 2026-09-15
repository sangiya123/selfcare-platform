package com.selfcare.platform.common.context;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reactive (WebFlux / Spring Cloud Gateway) counterpart to the servlet tenant resolver.
 *
 * Resolves the {@link RequestContext} from incoming headers, keeps it on the exchange, and forwards
 * it into the reactor context so downstream operators and WebClient calls can re-read it via
 * {@link #fromContext(ContextView)}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class RequestContextWebFilter implements WebFilter {

    public static final String CONTEXT_ATTRIBUTE = RequestContext.class.getName();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequest().getHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) headers.put(name, values.get(0));
        });

        RequestContext ctx = RequestContext.fromHeaders(headers);
        exchange.getAttributes().put(CONTEXT_ATTRIBUTE, ctx);

        MDC.put("correlationId", safeCorrelation(ctx));
        if (ctx.tenantId() != null) MDC.put("tenantId", ctx.tenantId());
        if (ctx.userId() != null) MDC.put("userId", ctx.userId());

        return chain.filter(exchange)
                .contextWrite(context -> context.put(CONTEXT_ATTRIBUTE, ctx))
                .doFinally(signal -> {
                    MDC.remove("correlationId");
                    MDC.remove("tenantId");
                    MDC.remove("userId");
                });
    }

    /** Read the context stored on the exchange (imperative access in a reactive controller). */
    public static RequestContext from(ServerWebExchange exchange) {
        RequestContext ctx = exchange.getAttribute(CONTEXT_ATTRIBUTE);
        return ctx != null ? ctx : RequestContext.empty();
    }

    /** Read the context from the reactor context view (used by WebClient operators). */
    public static RequestContext fromContext(ContextView view) {
        try {
            Object ctx = view.get(CONTEXT_ATTRIBUTE);
            return ctx instanceof RequestContext rc ? rc : RequestContext.empty();
        } catch (IllegalArgumentException ex) {
            return RequestContext.empty();
        }
    }

    private static String safeCorrelation(RequestContext ctx) {
        String c = ctx.correlationId();
        return c != null ? c : "selfcare-unset";
    }
}