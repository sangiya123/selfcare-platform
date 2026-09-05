package com.omobio.payment.repository;

import com.omobio.payment.domain.StepUpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StepUpRequestRepository extends JpaRepository<StepUpRequest, String> {

    Optional<StepUpRequest> findByCorrelationId(String correlationId);

    List<StepUpRequest> findByTenantIdAndUserIdAndStatus(String tenantId, String userId, String status);

    @Query("UPDATE StepUpRequest s SET s.status = 'EXPIRED' WHERE s.expiresAt < :cutoff AND s.status = 'PENDING'")
    int expireStale(@Param("cutoff") Instant cutoff);
}
