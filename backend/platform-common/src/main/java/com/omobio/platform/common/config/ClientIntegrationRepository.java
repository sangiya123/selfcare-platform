package com.omobio.platform.common.config;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientIntegrationRepository extends MongoRepository<ClientIntegrationConfig, String> {

    Optional<ClientIntegrationConfig> findByTenantIdAndIntegrationType(String tenantId, String integrationType);

    List<ClientIntegrationConfig> findByTenantId(String tenantId);

    List<ClientIntegrationConfig> findByIntegrationType(String integrationType);

    List<ClientIntegrationConfig> findByTenantIdAndIndustry(String tenantId, String industry);
}
