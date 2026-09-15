package com.selfcare.payment.repository;

import com.selfcare.payment.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, String> {

    Optional<PaymentMethod> findByTenantIdAndPaymentMethodId(String tenantId, String paymentMethodId);

    List<PaymentMethod> findByTenantIdAndUserIdAndStatusOrderByIsDefaultDescCreatedAtDesc(
            String tenantId, String userId, String status);

    List<PaymentMethod> findByTenantIdAndUserIdAndIsDefaultTrue(String tenantId, String userId);
}