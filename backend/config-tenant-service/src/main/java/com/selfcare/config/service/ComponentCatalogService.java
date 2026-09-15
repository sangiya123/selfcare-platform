package com.selfcare.config.service;

import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.repository.ComponentCatalogMongoRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Component catalog service — per-tenant registry of renderable widgets.
 *
 * Admin-authored (selfcare Studio palette), persisted in Mongo, and compiled
 * into the Experience Manifest `components` section on publish. Palette changes
 * are DB-level changes — never a selfcare-app release (v6 rule #1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentCatalogService {

    private final ComponentCatalogMongoRepository repository;

    private static final String GLOBAL_ENV = "*";

    public List<ComponentCatalogItem> listForTenant(String tenantId) {
        return repository.findByTenantIdOrderByLabelAsc(tenantId);
    }

    public List<ComponentCatalogItem> listActiveForCompile(String tenantId, String environment) {
        // Items published for a concrete environment win; unset environment ("*") acts as fallback.
        List<ComponentCatalogItem> scoped = repository
                .findByTenantIdAndEnvironmentAndStatus(tenantId, environment, "PUBLISHED");
        if (!scoped.isEmpty()) return scoped;
        return repository.findByTenantIdAndEnvironmentAndStatus(tenantId, GLOBAL_ENV, "PUBLISHED");
    }

    public ComponentCatalogItem get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new com.selfcare.platform.common.web.NotFoundException(
                        "ComponentCatalogItem", id));
    }

    /**
     * Create or update a catalog item (upsert by tenant + environment + componentId).
     * New items enter the palette in DRAFT; publish is the approval-gated step.
     */
    public ComponentCatalogItem save(ComponentCatalogItem item) {
        Instant now = Instant.now();
        if (item.getTenantId() == null || item.getTenantId().isBlank()) {
            item.setTenantId(TenantContext.get().getTenantId());
        }
        if (item.getEnvironment() == null || item.getEnvironment().isBlank()) {
            item.setEnvironment(GLOBAL_ENV);
        }
        repository.findByTenantIdAndEnvironmentAndComponentId(
                        item.getTenantId(), item.getEnvironment(), item.getComponentId())
                .ifPresent(existing -> {
                    item.setId(existing.getId());
                    item.setMongoVersion(existing.getMongoVersion());
                });

        if (item.getId() == null) item.setId(UUID.randomUUID().toString());
        // Non-null @Version marks the document as persistent so an existing
        // _id is updated, never re-inserted (avoid duplicate-key on reseed).
        if (item.getId() != null && item.getMongoVersion() == null) {
            item.setMongoVersion(0L);
        }
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(now);
            item.setCreatedBy(TenantContext.get().getUserId());
        }
        item.setUpdatedAt(now);
        if (item.getStatus() == null) item.setStatus("DRAFT");

        ComponentCatalogItem saved = repository.save(item);
        log.info("Component catalog saved: tenant={}, env={}, component={}, status={}",
                saved.getTenantId(), saved.getEnvironment(), saved.getComponentId(), saved.getStatus());
        return saved;
    }

    /** DRAFT -> PUBLISHED (enabled items only reach compiled manifests). */
    public ComponentCatalogItem publish(String id) {
        ComponentCatalogItem item = get(id);
        if ("PUBLISHED".equals(item.getStatus())) {
            return item;
        }
        item.setStatus("PUBLISHED");
        item.setPublishedAt(Instant.now());
        item.setPublishedBy(TenantContext.get().getUserId());
        ComponentCatalogItem saved = repository.save(item);
        log.info("Component catalog published: tenant={}, component={}",
                saved.getTenantId(), saved.getComponentId());
        return saved;
    }

    public void archive(String id) {
        ComponentCatalogItem item = get(id);
        item.setStatus("ARCHIVED");
        repository.save(item);
    }

    public void delete(String id) {
        repository.delete(get(id));
    }

    /** Convenience for seeding / bulk import — keeps dev environments consistent. */
    public ComponentCatalogItem upsert(ComponentCatalogItem item) {
        if (item.getStatus() == null) item.setStatus("DRAFT");
        if (item.getTenantId() == null || item.getEnvironment() == null) {
            throw new ConflictException("COMPONENT_CATALOG",
                    "tenantId and environment are required for upsert");
        }
        return save(item);
    }
}