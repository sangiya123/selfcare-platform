package com.selfcare.config.compiler;

import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.repository.ComponentCatalogMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Catalog lookup used by {@link ConfigCompiler} at compile time. Mirrors the
 * ThemeRepository / NavigationRepository compiler-facing pattern; the concrete
 * implementation reads PUBLISHED items from Mongo.
 */
@Component
@RequiredArgsConstructor
public class ComponentCatalogRepositoryImpl implements ConfigCompiler.ComponentCatalogRepository {

    private final ComponentCatalogMongoRepository mongo;

    private static final String GLOBAL_ENV = "*";

    @Override
    public List<ComponentCatalogItem> findActive(String tenantId, String environment) {
        // Items published for a concrete environment win; unset environment ("*") acts as fallback.
        List<ComponentCatalogItem> scoped = mongo.findByTenantIdAndEnvironmentAndStatus(tenantId, environment, "PUBLISHED");
        if (!scoped.isEmpty()) return scoped;
        return mongo.findByTenantIdAndEnvironmentAndStatus(tenantId, GLOBAL_ENV, "PUBLISHED");
    }
}