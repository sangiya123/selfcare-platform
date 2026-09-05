package com.omobio.payment.service;

import com.omobio.payment.domain.PaymentMethod;
import com.omobio.payment.repository.PaymentMethodRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ConflictException;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saved payment method service.
 *
 * Manages user's tokenized payment methods (cards, bank accounts, etc.).
 *
 * Important: This service never stores raw card data. Tokens are issued by
 * the payment provider (Stripe, Adyen, Dialog MIFE, etc.) and reference
 * vault records managed by the PSP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedPaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    @Transactional
    public PaymentMethod save(SavePaymentMethodRequest request) {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        // If marking as default, unmark others
        if (Boolean.TRUE.equals(request.getMakeDefault())) {
            List<PaymentMethod> existing = paymentMethodRepository
                    .findByTenantIdAndUserIdAndIsDefaultTrue(tenantId, userId);
            for (PaymentMethod pm : existing) {
                pm.setIsDefault(false);
                paymentMethodRepository.save(pm);
            }
        }

        PaymentMethod method = PaymentMethod.builder()
                .paymentMethodId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .methodType(request.getMethodType())
                .brand(request.getBrand())
                .lastFour(request.getLastFour())
                .expiryMonth(request.getExpiryMonth())
                .expiryYear(request.getExpiryYear())
                .cardholderName(request.getCardholderName())
                .paymentToken(request.getPaymentToken())
                .provider(request.getProvider())
                .isDefault(Boolean.TRUE.equals(request.getMakeDefault()))
                .status("ACTIVE")
                .nickname(request.getNickname())
                .build();

        method = paymentMethodRepository.save(method);
        log.info("Payment method saved: id={}, user={}, brand={}, lastFour={}",
                method.getPaymentMethodId(), userId, method.getBrand(), method.getLastFour());
        return method;
    }

    @Transactional(readOnly = true)
    public List<PaymentMethod> listForUser() {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();
        return paymentMethodRepository.findByTenantIdAndUserIdAndStatusOrderByIsDefaultDescCreatedAtDesc(
                tenantId, userId, "ACTIVE");
    }

    @Transactional
    public void delete(String paymentMethodId) {
        String tenantId = TenantContext.get().getTenantId();
        PaymentMethod method = paymentMethodRepository
                .findByTenantIdAndPaymentMethodId(tenantId, paymentMethodId)
                .orElseThrow(() -> new NotFoundException("PaymentMethod", paymentMethodId));
        method.setStatus("REVOKED");
        paymentMethodRepository.save(method);
        log.info("Payment method revoked: id={}", paymentMethodId);
    }

    @Transactional
    public PaymentMethod setDefault(String paymentMethodId) {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        PaymentMethod method = paymentMethodRepository
                .findByTenantIdAndPaymentMethodId(tenantId, paymentMethodId)
                .orElseThrow(() -> new NotFoundException("PaymentMethod", paymentMethodId));

        if (!"ACTIVE".equals(method.getStatus())) {
            throw new ConflictException("Cannot set inactive payment method as default");
        }

        // Unmark others
        List<PaymentMethod> existing = paymentMethodRepository
                .findByTenantIdAndUserIdAndIsDefaultTrue(tenantId, userId);
        for (PaymentMethod pm : existing) {
            if (!pm.getPaymentMethodId().equals(paymentMethodId)) {
                pm.setIsDefault(false);
                paymentMethodRepository.save(pm);
            }
        }

        method.setIsDefault(true);
        return paymentMethodRepository.save(method);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SavePaymentMethodRequest {
        private String methodType;
        private String brand;
        private String lastFour;
        private Integer expiryMonth;
        private Integer expiryYear;
        private String cardholderName;
        private String paymentToken;
        private String provider;
        private String nickname;
        private Boolean makeDefault;
    }
}