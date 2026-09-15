package com.selfcare.platform.common.context;

/**
 * Thread-local holder for the canonical {@link RequestContext}.
 *
 * Populated by the servlet {@code TenantResolverFilter} on every incoming request and cleared in a
 * finally block. Compatible with the legacy {@code TenantContext} (both are set together).
 *
 * <pre>{@code
 *   RequestContext ctx = RequestContextHolder.current();
 *   ctx.tenantId();        // tenant
 *   ctx.correlationId();   // tracing
 * }</pre>
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private RequestContextHolder() {}

    /** Current context, or a safe empty context when no request set it. */
    public static RequestContext get() {
        RequestContext ctx = CONTEXT.get();
        return ctx != null ? ctx : RequestContext.empty();
    }

    /** Current context, creating one when accessed outside a request. */
    public static RequestContext current() {
        RequestContext ctx = CONTEXT.get();
        if (ctx == null) {
            ctx = RequestContext.empty();
            CONTEXT.set(ctx);
        }
        return ctx;
    }

    public static void set(RequestContext ctx) {
        CONTEXT.set(ctx);
    }

    /** Clear the thread-local; must run at the end of every request and delegated task. */
    public static void clear() {
        CONTEXT.remove();
    }

    public static String tenantId() {
        return get().tenantId();
    }

    public static String correlationId() {
        return get().correlationId();
    }
}