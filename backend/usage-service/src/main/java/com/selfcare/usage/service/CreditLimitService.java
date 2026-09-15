package com.selfcare.usage.service;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.CreditLimitProvider;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Credit-limit service — resolves the tenant's {@link CreditLimitProvider}
 * from the adapter registry and returns the postpaid credit snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditLimitService {

    private final ApiAdapterRegistry<CreditLimitProvider> providerRegistry;

    /**
     * Fetch the credit limit for a connection.
     *
     * @param connectionId the connection identifier
     * @return the credit-limit snapshot
     * @throws ServiceUnavailableException when no provider is configured or the
     *         operator cannot supply a credit limit
     */
    public CreditLimitProvider.CreditLimit getCreditLimit(String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        try {
            CreditLimitProvider provider = providerRegistry.getProvider(tenantId);
            CreditLimitProvider.CreditLimit credit = provider.fetchCreditLimit(connectionId);
            if (credit == null) {
                throw new ServiceUnavailableException("CreditLimitProvider", "Null response", true);
            }
            return credit;
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Credit-limit fetch failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            throw new ServiceUnavailableException("CreditLimitProvider", e.getMessage(), true);
        }
    }
}