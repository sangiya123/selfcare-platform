package com.omobio.config.repository;

import com.omobio.config.domain.TenantConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for tenant master configuration.
 */
@Repository
public interface TenantConfigRepository extends MongoRepository<TenantConfig, String> {

    Optional<TenantConfig> findByTenantId(String tenantId);

    List<TenantConfig> findByIndustry(String industry);

    List<TenantConfig> findByStatus(String status);

    List<TenantConfig> findByIndustryAndCountry(String industry, String country);
}
