package com.selfcare.config.service;

import com.selfcare.config.domain.AssetDocument;
import com.selfcare.config.repository.AssetDocumentRepository;
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
 * Asset service — manages asset metadata documents.
 * Binary files are stored in object storage; this service manages metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetDocumentRepository repository;

    public List<AssetDocument> listForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    public Page<AssetDocument> search(String tenantId, String type, String status, Pageable pageable) {
        if (type != null && !type.isEmpty()) {
            return repository.findByTenantIdAndTypeAndStatus(tenantId, type, status != null ? status : "ACTIVE", pageable);
        }
        return repository.findByTenantIdAndStatus(tenantId, status != null ? status : "ACTIVE", pageable);
    }

    public AssetDocument get(String id) {
        String tenantId = TenantContext.get().getTenantId();
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Asset", id));
    }

    public AssetDocument getByTenantAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new NotFoundException("Asset", tenantId + "/" + id));
    }

    public AssetDocument save(AssetDocument asset) {
        if (asset.getId() == null) asset.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (asset.getCreatedAt() == null) {
            asset.setCreatedAt(now);
            asset.setCreatedBy(TenantContext.get().getUserId());
        }
        asset.setUpdatedAt(now);
        if (asset.getStatus() == null) asset.setStatus("ACTIVE");
        AssetDocument saved = repository.save(asset);
        log.info("Asset saved: tenant={}, id={}, type={}, filename={}",
                saved.getTenantId(), saved.getId(), saved.getType(), saved.getFilename());
        return saved;
    }

    public void delete(String id) {
        String tenantId = TenantContext.get().getTenantId();
        AssetDocument asset = getByTenantAndId(tenantId, id);
        asset.setStatus("DELETED");
        asset.setUpdatedAt(Instant.now());
        asset.setUpdatedBy(TenantContext.get().getUserId());
        repository.save(asset);
        log.info("Asset deleted: tenant={}, id={}", tenantId, id);
    }
}