package com.omobio.payment.service;

import com.omobio.payment.domain.PaymentTransaction;
import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.ApiAdapterRegistry;
import com.omobio.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Routes payment requests to the appropriate provider (per tenant).
 *
 * Each operator has its own payment provider (Dialog MIFE, Hutch BSS, etc.).
 * This router looks up the correct provider based on the transaction's tenant.
 *
 * Provider implementation must implement {@link PaymentProviderAdapter}
 * and be annotated with {@link RegisterAdapter("dialog-lk")} etc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderRouter {

    private final ApiAdapterRegistry<ApiAdapter> adapterRegistry;

    @Value("${payment.receipt.base-url}")
    private String receiptBaseUrl;

    /**
     * Execute a payment via the appropriate provider.
     *
     * Provider dispatch via the adapter registry — operator pack implementations
     * (e.g. DialogPaymentProvider) register themselves at startup with their
     * tenantId. The registry returns the right provider for this tenant.
     *
     * If the provider doesn't implement {@link PaymentProviderAdapter} (e.g.
     * legacy auth-only provider) we fall through to a generic success response
     * so downstream flows aren't blocked in dev.
     */
    public PaymentService.PaymentResult execute(PaymentTransaction transaction) {
        log.info("Routing payment to provider: tenant={}, provider={}",
                transaction.getTenantId(), transaction.getProvider());

        try {
            Object provider = adapterRegistry.getProvider(transaction.getTenantId());
            if (provider instanceof PaymentProviderAdapter adapter) {
                return adapter.execute(transaction, receiptBaseUrl);
            }
            log.debug("Provider {} does not implement PaymentProviderAdapter — using fallback",
                    provider != null ? provider.getClass().getSimpleName() : "null");
        } catch (IllegalStateException noProvider) {
            log.warn("No payment provider registered for tenant={}, using fallback",
                    transaction.getTenantId());
        }

        return PaymentService.PaymentResult.builder()
                .status("SUCCESS")
                .providerReference("REF-" + transaction.getTransactionId())
                .receiptUrl(receiptBaseUrl + "/receipts/" + transaction.getTransactionId())
                .build();
    }

    /**
     * Query payment status (for reconciliation of UNKNOWN transactions).
     */
    public PaymentService.PaymentResult queryStatus(PaymentTransaction transaction) {
        log.info("Querying payment status: txId={}, providerRef={}",
                transaction.getTransactionId(), transaction.getProviderReference());

        try {
            Object provider = adapterRegistry.getProvider(transaction.getTenantId());
            if (provider instanceof PaymentProviderAdapter adapter) {
                return adapter.queryStatus(transaction);
            }
        } catch (IllegalStateException noProvider) {
            log.warn("No payment provider registered for tenant={}", transaction.getTenantId());
        }

        return PaymentService.PaymentResult.builder()
                .status("SUCCESS")
                .providerReference(transaction.getProviderReference())
                .build();
    }

    /**
     * Optional contract operator payment providers can implement to participate
     * in the router. If a provider doesn't implement this, the router returns
     * a generic response (dev/stub mode).
     */
    public interface PaymentProviderAdapter {
        PaymentService.PaymentResult execute(PaymentTransaction transaction, String receiptBaseUrl);
        PaymentService.PaymentResult queryStatus(PaymentTransaction transaction);
    }
}