package com.omobio.billing.repository;

import com.omobio.billing.domain.BillingCycleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingCycleConfigRepository extends JpaRepository<BillingCycleConfig, String> {
    Optional<BillingCycleConfig> findByTenantId(String tenantId);
}
