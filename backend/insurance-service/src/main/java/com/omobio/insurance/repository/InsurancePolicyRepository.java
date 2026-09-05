package com.omobio.insurance.repository;

import com.omobio.platform.common.domain.insurance.InsurancePolicy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsurancePolicyRepository extends MongoRepository<InsurancePolicy, String> {

    List<InsurancePolicy> findByTenantIdAndCustomerId(String tenantId, String customerId);

    Optional<InsurancePolicy> findByTenantIdAndPolicyNumber(String tenantId, String policyNumber);

    Optional<InsurancePolicy> findByTenantIdAndInsurerPolicyRef(String tenantId, String insurerPolicyRef);

    List<InsurancePolicy> findByTenantId(String tenantId);

    long countByTenantIdAndStatus(String tenantId, InsurancePolicy.PolicyStatus status);
}
