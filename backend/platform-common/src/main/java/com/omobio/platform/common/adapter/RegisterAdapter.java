package com.omobio.platform.common.adapter;

import java.lang.annotation.*;

/**
 * Annotation to mark a class as a client-specific adapter implementation.
 *
 * Used by ApiAdapterRegistry.scanAndRegister() to discover and register adapters.
 *
 * Example:
 *   @Component
 *   @RegisterAdapter(tenantId = "dialog-lk", providerInterface = BalanceProvider.class)
 *   public class DialogBalanceProvider implements BalanceProvider {
 *       // ...
 *   }
 *
 * The annotation is optional — adapters can also be registered programmatically
 * via ApiAdapterRegistry.register() or via the industry-pack's adapter config
 * class (e.g. InsuranceAdapterConfig for the insurance industry pack).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RegisterAdapter {

    /**
     * The tenant/client ID this adapter serves (e.g., "dialog-lk", "aia-lk").
     */
    String tenantId();

    /**
     * The provider interface this adapter implements.
     * Required for type-safe registration.
     */
    Class<? extends ApiAdapter> providerInterface();

    /**
     * Priority order if multiple adapters match the same tenant.
     * Higher values win.
     */
    int priority() default 0;
}