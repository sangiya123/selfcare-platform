package com.selfcare.insurance.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.InsuranceProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires insurance client provider beans into the
 * {@link ApiAdapterRegistry} keyed by their {@code @RegisterAdapter} tenant ID.
 *
 * When the insurance-service starts:
 *  1. Spring instantiates all InsuranceProvider beans (AIA, future Allianz, etc.)
 *  2. We read each bean's @RegisterAdapter annotation to find the tenant ID
 *  3. We register it in the registry under that tenant ID
 *
 * The same provider class can serve multiple tenants (e.g. AIAInsuranceProvider
 * serves aia-lk, aia-sg, aia-th, etc.). We register it once per tenant
 * in the @PostConstruct hook by also inspecting the bean's adapterId() method.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsuranceAdapterConfig {

    private final ApiAdapterRegistry<InsuranceProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, InsuranceProvider> beans = applicationContext.getBeansOfType(InsuranceProvider.class);
        log.info("Discovered {} InsuranceProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, InsuranceProvider> entry : beans.entrySet()) {
            InsuranceProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered InsuranceProvider '{}' under client '{}'",
                        entry.getKey(), adapterId);
            }
        }

        // For multi-country insurance clients (e.g. AIA serves aia-lk, aia-sg, aia-th):
        // explicitly register the same provider instance under all AIA tenants.
        // The provider's loadConfig() reads tenant-specific URLs from the DB,
        // so one instance can serve multiple tenants.
        for (InsuranceProvider p : beans.values()) {
            String primaryId = p.getAdapterId();
            if (primaryId != null && primaryId.startsWith("aia-")) {
                for (String variant : new String[]{"aia-lk", "aia-sg", "aia-th", "aia-my", "aia-hk", "aia-in", "aia-cn"}) {
                    if (!variant.equals(primaryId)) {
                        registry.register(variant, p);
                    }
                }
                log.info("Registered AIA provider under all AIA market tenants");
            }
        }
    }
}
