package com.selfcare.insurance.repository;

import com.selfcare.platform.common.domain.insurance.InsuranceBeneficiary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InsuranceBeneficiaryRepository extends MongoRepository<InsuranceBeneficiary, String> {

    List<InsuranceBeneficiary> findByTenantIdAndPolicyId(String tenantId, String policyId);

    Optional<InsuranceBeneficiary> findByTenantIdAndId(String tenantId, String id);

    List<InsuranceBeneficiary> findByTenantIdAndCustomerId(String tenantId, String customerId);
}
