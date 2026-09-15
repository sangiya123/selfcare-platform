package com.selfcare.config.repository;

import com.selfcare.config.domain.ProductMappingDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for product mapping documents.
 */
@Repository
public interface ProductMappingDocumentRepository extends MongoRepository<ProductMappingDocument, String> {

    List<ProductMappingDocument> findByTenantId(String tenantId);

    Page<ProductMappingDocument> findByTenantId(String tenantId, Pageable pageable);

    List<ProductMappingDocument> findByTenantIdAndSourceProvider(String tenantId, String sourceProvider);

    List<ProductMappingDocument> findByTenantIdAndStatus(String tenantId, String status);

    Page<ProductMappingDocument> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    List<ProductMappingDocument> findByTenantIdAndCanonicalProductId(String tenantId, String canonicalProductId);

    Optional<ProductMappingDocument> findByTenantIdAndSourceProviderAndSourceProductId(
            String tenantId, String sourceProvider, String sourceProductId);

    Page<ProductMappingDocument> findByTenantIdAndSourceProvider(String tenantId, String sourceProvider, Pageable pageable);
}