package com.omobio.dialog.provider;

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
 * Spring configuration for Dialog industry-pack adapters.
 *
 * <p>At startup this class programmatically registers every Dialog provider
 * bean with the appropriate {@link ApiAdapterRegistry} for the corresponding
 * service. The individual provider classes are annotated with
 * {@link com.omobio.platform.common.adapter.RegisterAdapter} for discoverability,
 * but this config ensures explicit, ordered registration across all
 * service registries in one place.</p>
 *
 * <p>All provider config (URL, credentials) is read at runtime from MongoDB
 * via {@link com.omobio.platform.common.tenant.TenantConfigurationService}
 * — this config class does not hold any secrets.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DialogAdapterConfig {

    private final ApiAdapterRegistry<AuthProvider> authRegistry;
    private final ApiAdapterRegistry<BalanceProvider> balanceRegistry;
    private final ApiAdapterRegistry<NotificationChannelProvider> notificationRegistry;
    private final ApiAdapterRegistry<ProductCatalogProvider> catalogRegistry;

    // Provider beans injected via constructor — Spring auto-wires all @Component beans
    private final DialogAuthProvider dialogAuthProvider;
    private final DialogBalanceProvider dialogBalanceProvider;
    private final DialogNotificationProvider dialogNotificationProvider;
    private final DialogProductCatalogProvider dialogProductCatalogProvider;

    @PostConstruct
    public void register() {
        // Auth
        authRegistry.register("dialog-lk", dialogAuthProvider);
        log.info("Registered DialogAuthProvider -> ApiAdapterRegistry<AuthProvider>[dialog-lk]");

        // Balance
        balanceRegistry.register("dialog-lk", dialogBalanceProvider);
        log.info("Registered DialogBalanceProvider -> ApiAdapterRegistry<BalanceProvider>[dialog-lk]");

        // Notification (SMS)
        notificationRegistry.register("dialog-lk", dialogNotificationProvider);
        log.info("Registered DialogNotificationProvider -> ApiAdapterRegistry<NotificationChannelProvider>[dialog-lk]");

        // Product Catalog
        catalogRegistry.register("dialog-lk", dialogProductCatalogProvider);
        log.info("Registered DialogProductCatalogProvider -> ApiAdapterRegistry<ProductCatalogProvider>[dialog-lk]");

        // DialogPaymentProvider registers itself via @RegisterAdapter on PaymentProviderAdapter;
        // DialogProfileProvider is ad-hoc (no platform-wide registry contract).
        log.info("Dialog adapters registered: dialog-lk");
    }
}
