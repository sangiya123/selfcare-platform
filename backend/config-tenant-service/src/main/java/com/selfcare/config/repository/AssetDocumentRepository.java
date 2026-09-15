package com.selfcare.config.repository;

import com.selfcare.config.domain.AssetDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for asset documents.
 */
@Repository
public interface AssetDocumentRepository extends MongoRepository<AssetDocument, String> {

    List<AssetDocument> findByTenantId(String tenantId);

    List<AssetDocument> findByTenantIdAndType(String tenantId, String type);

    List<AssetDocument> findByTenantIdAndStatus(String tenantId, String status);

    Page<AssetDocument> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    Page<AssetDocument> findByTenantIdAndTypeAndStatus(String tenantId, String type, String status, Pageable pageable);

    @Query("{ 'tenantId': ?0, 'type': ?1, 'status': ?2 }")
    Page<AssetDocument> searchByTenantTypeStatus(String tenantId, String type, String status, Pageable pageable);

    Optional<AssetDocument> findByTenantIdAndId(String tenantId, String id);
}