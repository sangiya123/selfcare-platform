package com.omobio.config.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a navigation reference (e.g. "dialog-main@9") to a navigation
 * document stored in MongoDB.
 *
 * Navigation documents live in the {@code navigation_documents} collection
 * with fields: tenantId, name, version, items[].
 */
@Slf4j
@Component
public class NavigationRepositoryImpl implements ConfigCompiler.NavigationRepository {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public NavigationRepositoryImpl(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<JsonNode> findByRef(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        String[] parts = ref.split("@");
        String name = parts[0];
        String version = parts.length > 1 ? parts[1] : "1.0.0";

        String tenantId = com.omobio.platform.common.tenant.TenantContext.get().getTenantId();
        if (tenantId == null || "UNKNOWN".equals(tenantId)) {
            return Optional.empty();
        }

        try {
            Query query = new Query(Criteria.where("tenantId").is(tenantId)
                    .and("name").is(name)
                    .and("version").is(version)
                    .and("status").is("PUBLISHED"));
            var doc = mongoTemplate.findOne(query, Map.class, "navigation_documents");
            if (doc == null) {
                // Default navigation: empty items list
                return Optional.of(buildDefaultNavigation(name));
            }
            return Optional.of(objectMapper.valueToTree(doc));
        } catch (Exception e) {
            log.warn("Navigation lookup failed for ref={}: {}", ref, e.getMessage());
            return Optional.of(buildDefaultNavigation(name));
        }
    }

    private JsonNode buildDefaultNavigation(String name) {
        return objectMapper.valueToTree(
            Map.of("name", name, "items", List.of())
        );
    }
}
