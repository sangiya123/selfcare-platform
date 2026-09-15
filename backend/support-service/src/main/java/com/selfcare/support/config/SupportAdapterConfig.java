package com.selfcare.support.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.SupportProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires industry-pack {@link SupportProvider} beans into the
 * {@link ApiAdapterRegistry} keyed by their tenant ID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportAdapterConfig {

    private final ApiAdapterRegistry<SupportProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, SupportProvider> beans = applicationContext.getBeansOfType(SupportProvider.class);
        log.info("Discovered {} SupportProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, SupportProvider> entry : beans.entrySet()) {
            SupportProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered SupportProvider '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}