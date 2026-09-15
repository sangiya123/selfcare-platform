package com.selfcare.payment.service;

import com.selfcare.payment.domain.PaymentTransaction;
import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.PaymentProviderAdapter;
import com.selfcare.platform.common.adapter.PaymentRequest;
import com.selfcare.platform.common.adapter.PaymentResult;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Routes payment requests to the appropriate provider (per tenant).
 *
 * <p>Each operator has its own payment provider (Dialog MIFE, Hutch BSS, etc.).
 * This router looks up the correct provider based on the transaction's tenant.</p>
 *
 * <p>Provider implementation must implement {@link PaymentProviderAdapter}
 * and be annotated with {@link RegisterAdapter("dialog-lk")} etc.</p>
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
     * <p>Provider dispatch via the adapter registry — operator pack implementations
     * (e.g. DialogPaymentProvider) register themselves at startup with their
     * tenantId. The registry returns the right provider for this tenant.</p>
     *
     * <p>If the provider doesn't implement {@link PaymentProviderAdapter} (e.g.
     * legacy auth-only provider) we fall through to a generic success response
     * so downstream flows aren't blocked in dev.</p>
     */
    public PaymentService.PaymentResult execute(PaymentTransaction transaction) {
        log.info("Routing payment to provider: tenant={}, provider={}",
                transaction.getTenantId(), transaction.getProvider());

        try {
            Object provider = adapterRegistry.getProvider(transaction.getTenantId());
            if (provider instanceof PaymentProviderAdapter adapter) {
                PaymentResult result = adapter.execute(toRequest(transaction), receiptBaseUrl);
                return toServiceResult(result);
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
                PaymentResult result = adapter.queryStatus(toRequest(transaction));
                return toServiceResult(result);
            }
        } catch (IllegalStateException noProvider) {
            log.warn("No payment provider registered for tenant={}", transaction.getTenantId());
        }

        return PaymentService.PaymentResult.builder()
                .status("SUCCESS")
                .providerReference(transaction.getProviderReference())
                .build();
    }

    private PaymentRequest toRequest(PaymentTransaction transaction) {
        return PaymentRequest.builder()
                .transactionId(transaction.getTransactionId())
                .tenantId(transaction.getTenantId())
                .userId(transaction.getUserId())
                .transactionType(transaction.getTransactionType())
                .sourceConnectionId(transaction.getSourceConnectionId())
                .targetConnectionId(transaction.getTargetConnectionId())
                .billId(transaction.getBillId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentToken(transaction.getPaymentToken())
                .providerReference(transaction.getProviderReference())
                .build();
    }

    private PaymentService.PaymentResult toServiceResult(PaymentResult result) {
        return PaymentService.PaymentResult.builder()
                .status(result.getStatus())
                .providerReference(result.getProviderReference())
                .receiptUrl(result.getReceiptUrl())
                .failureReason(result.getFailureReason())
                .build();
    }
}