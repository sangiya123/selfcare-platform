package com.selfcare.config.repository;

import com.selfcare.config.domain.NavigationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for navigation documents.
 */
@Repository
public interface NavigationDocumentRepository extends MongoRepository<NavigationDocument, String> {

    Optional<NavigationDocument> findByTenantIdAndNameAndVersionAndStatus(
            String tenantId, String name, int version, String status);

    List<NavigationDocument> findByTenantIdAndStatus(String tenantId, String status);

    List<NavigationDocument> findByTenantId(String tenantId);

    Optional<NavigationDocument> findByTenantIdAndNameAndStatus(String tenantId, String name, String status);

    List<NavigationDocument> findByTenantIdAndNameOrderByVersionDesc(String tenantId, String name);
}