package com.selfcare.aia.provider;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.InsuranceProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring configuration class that registers the {@link AIAInsuranceProvider}
 * with the {@link ApiAdapterRegistry} for all six AIA market tenants.
 *
 * AIA is a multi-country insurer. The same {@link AIAInsuranceProvider}
 * class handles all markets — per-tenant config (base URL, credentials)
 * is resolved at runtime from {@code TenantConfigurationService} via the
 * {@code tenantId} parameter on every method call.
 *
 * Registration uses supplier-based registration so that the provider bean
 * is obtained from the Spring context (allowing dependency injection) and
 * then registered for each tenant.
 *
 * Tenants registered:
 * <ul>
 *   <li>aia-lk — Sri Lanka</li>
 *   <li>aia-sg — Singapore</li>
 *   <li>aia-th — Thailand</li>
 *   <li>aia-my — Malaysia</li>
 *   <li>aia-hk — Hong Kong</li>
 *   <li>aia-in — India</li>
 * </ul>
 *
 * This approach mirrors the {@link com.selfcare.config.seed.ClientIntegrationSeeder}
 * which seeds integration configs for all six AIA tenants pointing to the
 * same {@code AIAInsuranceProvider} class.
 *
 * @see ApiAdapterRegistry
 * @see AIAInsuranceProvider
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIAInsuranceAdapterConfig {

    /** All six AIA market tenants supported by this provider. */
    private static final List<String> AIA_TENANTS = List.of(
            "aia-lk",   // Sri Lanka
            "aia-sg",   // Singapore
            "aia-th",   // Thailand
            "aia-my",   // Malaysia
            "aia-hk",   // Hong Kong
            "aia-in"    // India
    );

    private final ApiAdapterRegistry<InsuranceProvider> registry;
    private final AIAInsuranceProvider aiaProvider;

    /**
     * Registers the {@link AIAInsuranceProvider} with the
     * {@link ApiAdapterRegistry} for each AIA market tenant.
     *
     * Called by Spring after the application context is fully initialized.
     */
    @PostConstruct
    public void register() {
        for (String tenantId : AIA_TENANTS) {
            registry.register(tenantId, () -> aiaProvider);
            log.info("Registered AIAInsuranceProvider for tenant: {}", tenantId);
        }
        log.info("AIAInsuranceAdapterConfig initialised — registered {} tenants: {}",
                AIA_TENANTS.size(), AIA_TENANTS);
    }
}
