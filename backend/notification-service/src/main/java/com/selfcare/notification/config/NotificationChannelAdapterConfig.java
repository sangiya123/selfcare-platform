package com.selfcare.notification.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.NotificationChannelProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires telco industry-pack {@link NotificationChannelProvider} beans into
 * the {@link ApiAdapterRegistry} keyed by their tenant ID.
 *
 * <p>At startup this finds every {@link NotificationChannelProvider} on the
 * classpath (Dialog SMSC, Hutch SMSC, Airtel, future operators) and
 * registers it under the tenant id returned by
 * {@link NotificationChannelProvider#getAdapterId()}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationChannelAdapterConfig {

    private final ApiAdapterRegistry<NotificationChannelProvider> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, NotificationChannelProvider> beans =
                applicationContext.getBeansOfType(NotificationChannelProvider.class);
        log.info("Discovered {} NotificationChannelProvider bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, NotificationChannelProvider> entry : beans.entrySet()) {
            NotificationChannelProvider provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered NotificationChannelProvider '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}