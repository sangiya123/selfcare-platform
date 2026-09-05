package com.omobio.config.repository;

import com.omobio.config.domain.FeatureFlag;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends MongoRepository<FeatureFlag, String> {

    List<FeatureFlag> findByTenantId(String tenantId);

    Optional<FeatureFlag> findByTenantIdAndName(String tenantId, String name);

    List<FeatureFlag> findByTenantIdAndEnabledEnvironmentsContaining(String tenantId, String environment);
}
