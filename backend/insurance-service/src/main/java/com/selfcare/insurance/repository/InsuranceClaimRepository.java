package com.selfcare.insurance.repository;

import com.selfcare.platform.common.domain.insurance.InsuranceClaim;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsuranceClaimRepository extends MongoRepository<InsuranceClaim, String> {

    List<InsuranceClaim> findByTenantIdAndCustomerId(String tenantId, String customerId);

    Optional<InsuranceClaim> findByTenantIdAndClaimNumber(String tenantId, String claimNumber);

    List<InsuranceClaim> findByTenantIdAndPolicyId(String tenantId, String policyId);

    long countByTenantIdAndStatus(String tenantId, InsuranceClaim.ClaimStatus status);
}
