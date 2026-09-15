package com.selfcare.config.service;

import com.selfcare.config.domain.ProductMappingDocument;
import com.selfcare.config.repository.ProductMappingDocumentRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Product mapping service — manages source-to-canonical product mappings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMappingService {

    private final ProductMappingDocumentRepository repository;

    public List<ProductMappingDocument> listForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    public Page<ProductMappingDocument> search(String tenantId, String sourceProvider, String status, Pageable pageable) {
        if (sourceProvider != null && !sourceProvider.isEmpty()) {
            return repository.findByTenantIdAndSourceProvider(tenantId, sourceProvider, pageable);
        }
        if (status != null && !status.isEmpty()) {
            return repository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return repository.findByTenantId(tenantId, pageable);
    }

    public ProductMappingDocument get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("ProductMapping", id));
    }

    public ProductMappingDocument getByTenantAndId(String tenantId, String id) {
        return repository.findById(id)
                .filter(m -> tenantId.equals(m.getTenantId()))
                .orElseThrow(() -> new NotFoundException("ProductMapping", tenantId + "/" + id));
    }

    public ProductMappingDocument save(ProductMappingDocument mapping) {
        if (mapping.getId() == null) mapping.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (mapping.getCreatedAt() == null) {
            mapping.setCreatedAt(now);
            mapping.setCreatedBy(TenantContext.get().getUserId());
        }
        mapping.setUpdatedAt(now);
        mapping.setUpdatedBy(TenantContext.get().getUserId());
        if (mapping.getStatus() == null) mapping.setStatus("ACTIVE");
        ProductMappingDocument saved = repository.save(mapping);
        log.info("Product mapping saved: tenant={}, source={}/{}, canonical={}",
                saved.getTenantId(), saved.getSourceProvider(), saved.getSourceProductId(), saved.getCanonicalProductId());
        return saved;
    }

    public ProductMappingDocument review(String id, boolean approved, String reviewer) {
        ProductMappingDocument mapping = get(id);
        mapping.setStatus(approved ? "ACTIVE" : "REJECTED");
        mapping.setReviewedBy(reviewer);
        mapping.setReviewedAt(Instant.now());
        mapping.setUpdatedAt(Instant.now());
        mapping.setUpdatedBy(reviewer);
        ProductMappingDocument saved = repository.save(mapping);
        log.info("Product mapping reviewed: id={}, approved={}", id, approved);
        return saved;
    }

    public void delete(String id) {
        ProductMappingDocument mapping = get(id);
        mapping.setStatus("DEPRECATED");
        mapping.setUpdatedAt(Instant.now());
        mapping.setUpdatedBy(TenantContext.get().getUserId());
        repository.save(mapping);
        log.info("Product mapping deprecated: id={}", id);
    }

    public List<ProductMappingDocument> bulkSave(List<ProductMappingDocument> mappings) {
        Instant now = Instant.now();
        String userId = TenantContext.get().getUserId();
        for (ProductMappingDocument m : mappings) {
            if (m.getId() == null) m.setId(UUID.randomUUID().toString());
            if (m.getCreatedAt() == null) {
                m.setCreatedAt(now);
                m.setCreatedBy(userId);
            }
            m.setUpdatedAt(now);
            m.setUpdatedBy(userId);
            if (m.getStatus() == null) m.setStatus("PENDING_REVIEW");
        }
        List<ProductMappingDocument> saved = repository.saveAll(mappings);
        log.info("Bulk saved {} product mappings for tenant={}", saved.size(), TenantContext.get().getTenantId());
        return saved;
    }
}