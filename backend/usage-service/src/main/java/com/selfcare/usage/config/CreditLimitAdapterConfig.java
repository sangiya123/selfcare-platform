package com.selfcare.usage.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.CreditLimitProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires telco industry-pack {@link CreditLimitProvider} beans into the
 * {@link ApiAdapterRegistry} keyed by their tenant ID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditLimitAdapterConfig {

    private final ApiAdapterRegistry<CreditLimitProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, CreditLimitProvider> beans = applicationContext.getBeansOfType(CreditLimitProvider.class);
        log.info("Discovered {} CreditLimitProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, CreditLimitProvider> entry : beans.entrySet()) {
            CreditLimitProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered CreditLimitProvider '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}