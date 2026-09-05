package com.omobio.config.repository;

import com.omobio.config.domain.LayoutDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for layout documents.
 */
@Repository
public interface LayoutDocumentRepository extends MongoRepository<LayoutDocument, String> {

    Optional<LayoutDocument> findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
            String tenantId, String environment, String experience, String profileKey, String status);

    List<LayoutDocument> findByTenantIdAndStatus(String tenantId, String status);

    List<LayoutDocument> findByTenantId(String tenantId);

    List<LayoutDocument> findByTenantIdAndExperienceAndStatus(
            String tenantId, String experience, String status);
}
