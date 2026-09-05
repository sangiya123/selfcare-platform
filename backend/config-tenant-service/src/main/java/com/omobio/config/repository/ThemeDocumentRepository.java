package com.omobio.config.repository;

import com.omobio.config.domain.ThemeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for theme documents.
 */
@Repository
public interface ThemeDocumentRepository extends MongoRepository<ThemeDocument, String> {

    Optional<ThemeDocument> findByTenantIdAndNameAndVersionAndStatus(
            String tenantId, String name, String version, String status);

    List<ThemeDocument> findByTenantIdAndStatus(String tenantId, String status);

    List<ThemeDocument> findByTenantId(String tenantId);
}
