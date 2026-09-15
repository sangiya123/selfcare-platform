package com.selfcare.config.repository;

import com.selfcare.config.domain.ComponentCatalogItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Mongo data access for the component catalog.
 */
public interface ComponentCatalogMongoRepository
        extends MongoRepository<ComponentCatalogItem, String> {

    List<ComponentCatalogItem> findByTenantIdOrderByLabelAsc(String tenantId);

    List<ComponentCatalogItem> findByTenantIdAndEnvironmentAndStatus(
            String tenantId, String environment, String status);

    Optional<ComponentCatalogItem> findByTenantIdAndEnvironmentAndComponentId(
            String tenantId, String environment, String componentId);

    Optional<ComponentCatalogItem> findByTenantIdAndEnvironmentAndComponentIdAndStatus(
            String tenantId, String environment, String componentId, String status);

    void deleteByTenantIdAndEnvironmentAndComponentId(
            String tenantId, String environment, String componentId);
}