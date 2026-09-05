package com.omobio.hutch.provider;

import com.omobio.notification.adapter.NotificationChannelProvider;
import com.omobio.platform.common.adapter.ApiAdapterRegistry;
import com.omobio.platform.common.adapter.AuthProvider;
import com.omobio.product.adapter.ProductCatalogProvider;
import com.omobio.usage.adapter.BalanceProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Hutch industry-pack adapters.
 *
 * <p>At startup this class programmatically registers every Hutch provider
 * bean with the appropriate {@link ApiAdapterRegistry} for the corresponding
 * service. The individual provider classes are annotated with
 * {@link com.omobio.platform.common.adapter.RegisterAdapter} for discoverability,
 * but this config ensures explicit, ordered registration across all
 * service registries in one place.</p>
 *
 * <p>All provider config (URL, credentials) is read at runtime from MongoDB
 * via {@link com.omobio.platform.common.tenant.TenantConfigurationService}.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HutchAdapterConfig {

    private final ApiAdapterRegistry<AuthProvider> authRegistry;
    private final ApiAdapterRegistry<BalanceProvider> balanceRegistry;
    private final ApiAdapterRegistry<NotificationChannelProvider> notificationRegistry;
    private final ApiAdapterRegistry<ProductCatalogProvider> catalogRegistry;

    // Provider beans injected via constructor
    private final HutchAuthProvider hutchAuthProvider;
    private final HutchBalanceProvider hutchBalanceProvider;
    private final HutchNotificationProvider hutchNotificationProvider;
    private final HutchProductCatalogProvider hutchProductCatalogProvider;

    @PostConstruct
    public void register() {
        authRegistry.register("hutch-lk", hutchAuthProvider);
        log.info("Registered HutchAuthProvider -> ApiAdapterRegistry<AuthProvider>[hutch-lk]");

        balanceRegistry.register("hutch-lk", hutchBalanceProvider);
        log.info("Registered HutchBalanceProvider -> ApiAdapterRegistry<BalanceProvider>[hutch-lk]");

        notificationRegistry.register("hutch-lk", hutchNotificationProvider);
        log.info("Registered HutchNotificationProvider -> ApiAdapterRegistry<NotificationChannelProvider>[hutch-lk]");

        catalogRegistry.register("hutch-lk", hutchProductCatalogProvider);
        log.info("Registered HutchProductCatalogProvider -> ApiAdapterRegistry<ProductCatalogProvider>[hutch-lk]");

        // HutchPaymentProvider registers itself via @RegisterAdapter on PaymentProviderAdapter
        log.info("Hutch adapters registered: hutch-lk");
    }
}
