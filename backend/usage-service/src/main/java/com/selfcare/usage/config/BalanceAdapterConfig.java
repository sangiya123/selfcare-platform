package com.selfcare.usage.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.BalanceProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires telco industry-pack {@link BalanceProvider} beans into the
 * {@link ApiAdapterRegistry} keyed by their tenant ID.
 *
 * <p>At startup this finds every {@link BalanceProvider} on the classpath
 * (Dialog, Hutch, Airtel, future operators) and registers it under the
 * tenant id returned by {@link BalanceProvider#getAdapterId()}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceAdapterConfig {

    private final ApiAdapterRegistry<BalanceProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, BalanceProvider> beans = applicationContext.getBeansOfType(BalanceProvider.class);
        log.info("Discovered {} BalanceProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, BalanceProvider> entry : beans.entrySet()) {
            BalanceProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered BalanceProvider '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}