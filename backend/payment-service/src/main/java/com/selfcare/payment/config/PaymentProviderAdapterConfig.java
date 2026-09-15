package com.selfcare.payment.config;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.PaymentProviderAdapter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wires telco industry-pack {@link PaymentProviderAdapter} beans into the
 * {@link ApiAdapterRegistry} keyed by their tenant ID.
 *
 * <p>At startup this finds every {@link PaymentProviderAdapter} on the
 * classpath (Dialog MIFE, Hutch BSS, Airtel Money, future operators) and
 * registers it under the tenant id returned by
 * {@link PaymentProviderAdapter#getAdapterId()}. The
 * {@link com.selfcare.payment.service.PaymentProviderRouter} then dispatches
 * transactions via {@code ApiAdapterRegistry#getProvider(tenantId)}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProviderAdapterConfig {

    private final ApiAdapterRegistry<PaymentProviderAdapter> registry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void registerAll() {
        Map<String, PaymentProviderAdapter> beans =
                applicationContext.getBeansOfType(PaymentProviderAdapter.class);
        log.info("Discovered {} PaymentProviderAdapter bean(s): {}", beans.size(), beans.keySet());

        for (Map.Entry<String, PaymentProviderAdapter> entry : beans.entrySet()) {
            PaymentProviderAdapter provider = entry.getValue();
            String adapterId = provider.getAdapterId();
            if (adapterId != null && !adapterId.isBlank()) {
                registry.register(adapterId, provider);
                log.info("Registered PaymentProviderAdapter '{}' under tenant '{}'",
                        entry.getKey(), adapterId);
            }
        }
    }
}