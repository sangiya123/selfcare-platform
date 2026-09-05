package com.omobio.insurance.repository;

import com.omobio.platform.common.domain.insurance.InsurancePremium;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsurancePremiumRepository extends MongoRepository<InsurancePremium, String> {

    List<InsurancePremium> findByTenantIdAndPolicyId(String tenantId, String policyId);

    List<InsurancePremium> findByTenantIdAndCustomerId(String tenantId, String customerId);

    Optional<InsurancePremium> findByTenantIdAndId(String tenantId, String id);
}
