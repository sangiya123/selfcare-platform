package com.omobio.platform.common.tenant;


/**
 * Request-scoped holder for the current tenant identity.
 *
 * Set by TenantResolverFilter on every incoming request.
 * Accessible anywhere in the call chain via TenantContext.get().
 *
 * Example:
 *   String tenantId = TenantContext.get().getTenantId();
 *
 * @see TenantResolverFilter
 */
public class TenantContext {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private String tenantId;
    private String environment;
    private String userId;
    private String sessionId;
    private String correlationId;
    private String token;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    /**
     * Get the current tenant context from the thread-local store.
     * Returns empty context if no filter has set it yet.
     */
    public static TenantContext get() {
        TenantContext ctx = CONTEXT.get();
        return ctx != null ? ctx : empty();
    }

    /**
     * Get the current context, creating one when called outside a request.
     */
    public static TenantContext current() {
        return currentOrCreate();
    }

    /**
     * Set the current tenant context.
     */
    public static void set(TenantContext ctx) {
        CONTEXT.set(ctx);
    }

    /**
     * Clear the current tenant context.
     * Must be called at the end of request processing (TenantResolverFilter does this).
     */
    public static void clear() {
        CONTEXT.remove();
    }

    private static TenantContext empty() {
        TenantContext ctx = new TenantContext();
        ctx.tenantId = "UNKNOWN";
        return ctx;
    }

    private static TenantContext currentOrCreate() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null) {
            ctx = new TenantContext();
            CONTEXT.set(ctx);
        }
        return ctx;
    }

    @Override
    public String toString() {
        return "TenantContext{" +
                "tenantId='" + tenantId + '\'' +
                ", environment='" + environment + '\'' +
                ", userId='" + userId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }
}
