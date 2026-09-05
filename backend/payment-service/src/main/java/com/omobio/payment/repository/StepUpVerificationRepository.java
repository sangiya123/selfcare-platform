package com.omobio.payment.repository;

import com.omobio.payment.domain.StepUpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StepUpVerificationRepository extends JpaRepository<StepUpVerification, Long> {

    List<StepUpVerification> findByCorrelationId(String correlationId);

    List<StepUpVerification> findByTenantIdAndUserIdOrderByVerifiedAtDesc(String tenantId, String userId);
}
