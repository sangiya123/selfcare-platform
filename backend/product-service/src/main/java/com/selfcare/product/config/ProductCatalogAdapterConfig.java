package com.selfcare.product.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.ProductCatalogProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires telco industry-pack {@link ProductCatalogProvider} beans into the
 * {@link ApiAdapterRegistry} keyed by their tenant ID.
 *
 * <p>At startup this finds every {@link ProductCatalogProvider} on the
 * classpath (Dialog VAS, Hutch BSS, Airtel, future operators) and registers
 * it under the tenant id returned by
 * {@link ProductCatalogProvider#getAdapterId()}. The
 * {@link com.selfcare.product.service.ProductMaterializationService} then
 * aggregates catalogs per tenant via the registry.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCatalogAdapterConfig {

    private final ApiAdapterRegistry<ProductCatalogProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, ProductCatalogProvider> beans =
                applicationContext.getBeansOfType(ProductCatalogProvider.class);
        log.info("Discovered {} ProductCatalogProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, ProductCatalogProvider> entry : beans.entrySet()) {
            ProductCatalogProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered ProductCatalogProvider '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}