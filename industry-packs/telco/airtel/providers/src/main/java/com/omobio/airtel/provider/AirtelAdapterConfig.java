package com.omobio.airtel.provider;

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
 * Spring configuration for Airtel industry-pack adapters.
 *
 * <p>At startup this class programmatically registers every Airtel provider
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
public class AirtelAdapterConfig {

    private final ApiAdapterRegistry<AuthProvider> authRegistry;
    private final ApiAdapterRegistry<BalanceProvider> balanceRegistry;
    private final ApiAdapterRegistry<NotificationChannelProvider> notificationRegistry;
    private final ApiAdapterRegistry<ProductCatalogProvider> catalogRegistry;

    // Provider beans injected via constructor
    private final AirtelAuthProvider airtelAuthProvider;
    private final AirtelBalanceProvider airtelBalanceProvider;
    private final AirtelNotificationProvider airtelNotificationProvider;
    private final AirtelProductCatalogProvider airtelProductCatalogProvider;

    @PostConstruct
    public void register() {
        authRegistry.register("airtel-lk", airtelAuthProvider);
        log.info("Registered AirtelAuthProvider -> ApiAdapterRegistry<AuthProvider>[airtel-lk]");

        balanceRegistry.register("airtel-lk", airtelBalanceProvider);
        log.info("Registered AirtelBalanceProvider -> ApiAdapterRegistry<BalanceProvider>[airtel-lk]");

        notificationRegistry.register("airtel-lk", airtelNotificationProvider);
        log.info("Registered AirtelNotificationProvider -> ApiAdapterRegistry<NotificationChannelProvider>[airtel-lk]");

        catalogRegistry.register("airtel-lk", airtelProductCatalogProvider);
        log.info("Registered AirtelProductCatalogProvider -> ApiAdapterRegistry<ProductCatalogProvider>[airtel-lk]");

        // AirtelPaymentProvider registers itself via @RegisterAdapter on PaymentProviderAdapter
        log.info("Airtel adapters registered: airtel-lk");
    }
}
