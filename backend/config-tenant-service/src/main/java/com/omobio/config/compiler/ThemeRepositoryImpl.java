package com.omobio.config.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.config.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves a theme reference (e.g. "dialog-default@17") to the published
 * theme document for the given tenant.
 */
@Component
@RequiredArgsConstructor
public class ThemeRepositoryImpl implements ConfigCompiler.ThemeRepository {

    private final ThemeService themeService;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<JsonNode> findByRef(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        // ref format: "name@version" (version is optional)
        String[] parts = ref.split("@");
        String name = parts[0];
        String version = parts.length > 1 ? parts[1] : "1.0.0";

        // Resolve tenant from current request context
        String tenantId = com.omobio.platform.common.tenant.TenantContext.get().getTenantId();
        if (tenantId == null || "UNKNOWN".equals(tenantId)) {
            return Optional.empty();
        }

        try {
            var theme = themeService.getByRef(tenantId, name, version);
            return Optional.of(objectMapper.valueToTree(theme));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
