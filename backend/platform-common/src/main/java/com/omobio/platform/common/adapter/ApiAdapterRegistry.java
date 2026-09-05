package com.omobio.platform.common.adapter;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry for operator-specific API adapters.
 *
 * This is the core of the "one product, zero forks" pattern.
 * Each service defines provider interfaces (e.g., BalanceProvider, PaymentProvider)
 * that extend ApiAdapter. Operators provide implementations of these interfaces.
 *
 * At startup, each operator's adapter package is scanned and adapters are registered.
 * At runtime, services call registry.getProvider(tenantId) to get the operator-specific
 * implementation for that tenant.
 *
 * Example usage in a service:
 *   @Autowired
 *   private ApiAdapterRegistry<BalanceProvider> balanceProviderRegistry;
 *
 *   public BalanceResponse getBalance(String connectionId, String tenantId) {
 *       BalanceProvider provider = balanceProviderRegistry.getProvider(tenantId);
 *       return provider.getBalance(connectionId, tenantId);
 *   }
 *
 * Registration is done via a @PostConstruct config class in each service:
 *   @Component
 *   @RequiredArgsConstructor
 *   public class BalanceAdapterConfig {
 *       private final ApiAdapterRegistry<BalanceProvider> registry;
 *       @PostConstruct
 *       public void register() {
 *           registry.register("dialog-lk", DialogBalanceProvider::new);
 *           registry.register("hutch-lk", HutchBalanceProvider::new);
 *           // or scan package: registry.scanAndRegister("com.omobio.providers.dialog");
 *       }
 *   }
 *
 * @param <T> The provider interface type (e.g., BalanceProvider)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAdapterRegistry<T extends ApiAdapter> {

    private final Map<String, T> adapters = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends T>> adapterClasses = new ConcurrentHashMap<>();
    private final Map<String, Supplier<T>> adapterSuppliers = new ConcurrentHashMap<>();

    /**
     * Register an adapter instance for a specific tenant.
     * Replaces any existing adapter for that tenant.
     */
    public synchronized void register(String tenantId, T adapter) {
        String key = tenantId.toLowerCase();
        if (adapters.containsKey(key)) {
            log.info("Replacing adapter for tenant {}: {} -> {}",
                    key, adapters.get(key).getAdapterId(), adapter.getAdapterId());
            adapters.get(key).destroy();
        }
        adapters.put(key, adapter);
        @SuppressWarnings("unchecked")
        Class<? extends T> clazz = (Class<? extends T>) adapter.getClass();
        adapterClasses.put(key, clazz);
        log.info("Registered adapter for tenant {}: {}", key, adapter.getAdapterId());
    }

    /**
     * Register an adapter supplier (factory) for lazy initialization.
     */
    public synchronized void register(String tenantId, Supplier<T> supplier) {
        String key = tenantId.toLowerCase();
        adapterSuppliers.put(key, supplier);
        log.info("Registered adapter supplier for tenant: {}", key);
    }

    /**
     * Register an adapter class for reflective instantiation.
     */
    public synchronized void register(String tenantId, Class<? extends T> adapterClass) {
        String key = tenantId.toLowerCase();
        adapterClasses.put(key, adapterClass);
        log.info("Registered adapter class for tenant {}: {}", key, adapterClass.getName());
    }

    /**
     * Get the adapter for a specific tenant.
     * Lazily instantiates from supplier or class if not already instantiated.
     *
     * @param tenantId The tenant identifier
     * @return The adapter instance, or throws IllegalStateException if not registered
     */
    @SuppressWarnings("unchecked")
    public T getProvider(String tenantId) {
        String key = tenantId.toLowerCase();

        // Return cached instance if exists
        T cached = adapters.get(key);
        if (cached != null) {
            return cached;
        }

        // Try supplier first
        Supplier<T> supplier = adapterSuppliers.get(key);
        if (supplier != null) {
            T adapter = supplier.get();
            adapters.put(key, adapter);
            return adapter;
        }

        // Try reflective instantiation
        Class<? extends T> adapterClass = adapterClasses.get(key);
        if (adapterClass != null) {
            try {
                T adapter = adapterClass.getDeclaredConstructor().newInstance();
                adapters.put(key, adapter);
                return adapter;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to instantiate adapter class for tenant " + key + ": " + adapterClass.getName(), e);
            }
        }

        // Check for a default/fallback adapter
        T fallback = adapters.get("default");
        if (fallback != null) {
            log.warn("No adapter registered for tenant {}, using fallback", key);
            return fallback;
        }

        throw new IllegalStateException(
                "No adapter registered for tenant: " + key + ". Registered tenants: " + adapters.keySet());
    }

    /**
     * Get the adapter for the current request's tenant (from TenantContext).
     */
    public T getProvider() {
        String tenantId = com.omobio.platform.common.tenant.TenantContext.get().getTenantId();
        return getProvider(tenantId);
    }

    /**
     * Register multiple adapters from a list.
     */
    public void registerAll(List<Registration<T>> registrations) {
        for (Registration<T> reg : registrations) {
            register(reg.getTenantId(), reg.getAdapter());
        }
    }

    /**
     * Data class for batch registration.
     */
    @lombok.Value
    public static class Registration<T extends ApiAdapter> {
        String tenantId;
        T adapter;
    }

    /**
     * Check if an adapter is registered for a tenant.
     */
    public boolean hasAdapter(String tenantId) {
        String key = tenantId.toLowerCase();
        return adapters.containsKey(key) || adapterClasses.containsKey(key) || adapterSuppliers.containsKey(key);
    }

    /**
     * Get all registered tenant IDs.
     */
    public java.util.Set<String> getRegisteredTenants() {
        return adapters.keySet();
    }

    /**
     * Remove an adapter (for hot-reload or testing).
     */
    public synchronized void unregister(String tenantId) {
        String key = tenantId.toLowerCase();
        T adapter = adapters.remove(key);
        if (adapter != null) {
            adapter.destroy();
        }
        adapterClasses.remove(key);
        adapterSuppliers.remove(key);
        log.info("Unregistered adapter for tenant: {}", key);
    }

    /**
     * Clear all adapters.
     */
    public synchronized void clear() {
        adapters.values().forEach(ApiAdapter::destroy);
        adapters.clear();
        adapterClasses.clear();
        adapterSuppliers.clear();
    }

    /**
     * Scan a package for adapter implementations and register them.
     * Uses Spring's ClassPathScanningCandidateComponentProvider.
     * Each class must have a no-arg constructor and be annotated with @Component or similar.
     */
    public void scanAndRegister(String basePackage) {
        org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider scanner =
                new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AssignableTypeFilter(
                (Class<T>) ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
                        .getActualTypeArguments()[0]));

        for (org.springframework.beans.factory.config.BeanDefinition beanDef : scanner.findCandidateComponents(basePackage)) {
            try {
                Class<?> clazz = Class.forName(beanDef.getBeanClassName());
                if (ApiAdapter.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends T> adapterClass = (Class<? extends T>) clazz;
                    // Try to extract tenant ID from class name or package
                    String tenantId = extractTenantIdFromClass(clazz);
                    if (tenantId != null) {
                        register(tenantId, adapterClass);
                    }
                }
            } catch (ClassNotFoundException e) {
                log.warn("Failed to load adapter class: {}", beanDef.getBeanClassName());
            }
        }
    }

    private String extractTenantIdFromClass(Class<?> clazz) {
        String name = clazz.getSimpleName().toLowerCase();
        String pkg = clazz.getPackageName().toLowerCase();

        // Telco industry pack patterns: DialogBalanceProvider -> dialog, etc.
        for (String client : new String[]{"dialog", "hutch", "airtel"}) {
            if (name.contains(client) || pkg.contains(client)) {
                return client + "-lk"; // Default country
            }
        }

        // Insurance industry pack patterns: AIAInsuranceProvider -> aia-lk.
        // Insurance providers typically serve multiple tenants (countries) under
        // the same provider class — see InsuranceAdapterConfig for multi-tenant
        // registration. This extractor is only a fallback for programmatic
        // registration without annotation resolution.
        if (name.contains("aia") || pkg.contains("aia")) {
            return "aia-lk";
        }

        return null;
    }
}