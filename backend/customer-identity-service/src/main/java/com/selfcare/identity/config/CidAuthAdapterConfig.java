package com.selfcare.identity.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.CidAuthProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires industry-pack {@link CidAuthProvider} beans into the
 * {@link ApiAdapterRegistry} keyed by their tenant ID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CidAuthAdapterConfig {

    private final ApiAdapterRegistry<CidAuthProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, CidAuthProvider> beans = applicationContext.getBeansOfType(CidAuthProvider.class);
        log.info("Discovered {} CidAuthProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, CidAuthProvider> entry : beans.entrySet()) {
            CidAuthProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered CidAuthProvider '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}