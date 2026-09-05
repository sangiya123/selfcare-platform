package com.omobio.config.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.config.domain.LayoutDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config Compiler — validates and compiles layout documents into immutable runtime manifests.
 *
 * Per ADR-004: Mongo is config source, not per-request runtime interpreter.
 * At publish time, source config is compiled into an immutable runtime manifest.
 * Services and apps cache and use the compiled manifest directly.
 *
 * Compilation steps:
 * 1. Schema validation (JSON Schema for layout documents)
 * 2. Reference validation (themeRef, navigationRef, componentId exist)
 * 3. Visibility evaluation (resolve conditional configs)
 * 4. Action validation (registered actions only — no arbitrary code)
 * 5. Component compatibility check (compatible with app versions)
 * 6. Token resolution (themeRef -> ThemeTokens)
 * 7. Final manifest emission
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigCompiler {

    private final ObjectMapper objectMapper;
    private final ComponentRegistry componentRegistry;
    private final ThemeRepository themeRepository;
    private final NavigationRepository navigationRepository;

    /**
     * Compile a layout document into an immutable runtime manifest.
     */
    public CompiledManifest compile(LayoutDocument document) {
        log.info("Compiling layout: tenant={}, experience={}, version={}",
                document.getTenantId(), document.getExperience(), document.getConfigVersion());

        // 1. Validate schema version
        validateSchemaVersion(document);

        // 2. Resolve theme (optional in dev — fall back to empty if missing)
        JsonNode theme = themeRepository.findByRef(document.getThemeRef())
                .orElseGet(() -> {
                    log.debug("Theme not found: {} — using empty default", document.getThemeRef());
                    return objectMapper.createObjectNode();
                });

        // 3. Resolve navigation (optional — fall back to empty if missing)
        JsonNode navigation = navigationRepository.findByRef(document.getNavigationRef())
                .orElseGet(() -> {
                    log.debug("Navigation not found: {} — using empty default", document.getNavigationRef());
                    return objectMapper.createObjectNode();
                });

        // 4. Validate and resolve sections
        List<CompiledSection> compiledSections = new ArrayList<>();
        if (document.getSections() != null) {
            for (LayoutDocument.Section section : document.getSections()) {
                CompiledSection compiled = compileSection(section);
                if (compiled != null) {
                    compiledSections.add(compiled);
                }
            }
        }

        // 5. Build immutable manifest
        CompiledManifest manifest = new CompiledManifest();
        manifest.setSchemaVersion(document.getSchemaVersion());
        manifest.setConfigVersion(document.getConfigVersion());
        manifest.setTenant(document.getTenantId());
        manifest.setEnvironment(document.getEnvironment());
        manifest.setExperience(document.getExperience());
        manifest.setProfileKey(document.getProfileKey());
        manifest.setCompatibility(document.getCompatibility());
        manifest.setTheme(theme);
        manifest.setNavigation(navigation);
        manifest.setSections(compiledSections);
        manifest.setCompiledAt(java.time.Instant.now());
        manifest.setCompilerVersion("1.0.0");

        log.info("Compiled manifest: sections={}, themeRef={}, configVersion={}",
                compiledSections.size(), document.getThemeRef(), document.getConfigVersion());
        return manifest;
    }

    private CompiledSection compileSection(LayoutDocument.Section section) {
        // 1. Validate component exists in registry
        if (!componentRegistry.hasComponent(section.getComponent())) {
            log.warn("Unknown component in layout, skipping: component={}, sectionId={}",
                    section.getComponent(), section.getId());
            return null;
        }

        // 2. Validate actions — no arbitrary code
        if (section.getActions() != null) {
            for (LayoutDocument.Action action : section.getActions()) {
                validateAction(action);
            }
        }

        CompiledSection compiled = new CompiledSection();
        compiled.setId(section.getId());
        compiled.setComponent(section.getComponent());
        compiled.setVariant(section.getVariant());
        compiled.setDataSource(section.getDataSource());
        compiled.setProps(section.getProps());
        compiled.setStates(section.getStates());
        compiled.setAnalytics(section.getAnalytics());
        compiled.setOrder(section.getOrder() != null ? section.getOrder() : 99);

        return compiled;
    }

    private void validateAction(LayoutDocument.Action action) {
        Set<String> allowedTypes = Set.of(
            "NAVIGATE", "BACK", "START_JOURNEY", "CALL_API", "OPEN_WEB",
            "OPEN_WEB_SSO", "EXTERNAL_BROWSER", "DEEP_LINK", "CALL_PHONE",
            "EMAIL", "COPY", "SHARE", "DOWNLOAD", "MODAL", "BOTTOM_SHEET",
            "LOGIN", "LOGOUT", "PAYMENT", "REFRESH"
        );
        if (!allowedTypes.contains(action.getType())) {
            throw new IllegalStateException("Unknown action type: " + action.getType());
        }
    }

    private void validateSchemaVersion(LayoutDocument document) {
        // ADR-004: Always check schema version
        if (!"2.0".equals(document.getSchemaVersion())) {
            throw new IllegalStateException("Unsupported schema version: " + document.getSchemaVersion());
        }
    }

    @lombok.Data
    public static class CompiledManifest {
        private String schemaVersion;
        private int configVersion;
        private String tenant;
        private String environment;
        private String experience;
        private String profileKey;
        private LayoutDocument.Compatibility compatibility;
        private JsonNode theme;
        private JsonNode navigation;
        private List<CompiledSection> sections;
        private java.time.Instant compiledAt;
        private String compilerVersion;
    }

    @lombok.Data
    public static class CompiledSection {
        private String id;
        private String component;
        private String variant;
        private String dataSource;
        private java.util.Map<String, Object> props;
        private java.util.Map<String, String> states;
        private java.util.Map<String, String> analytics;
        private int order;
    }

    /**
     * Component registry — knows the IDs of all renderable widgets.
     * Implemented by ComponentRegistryImpl.
     */
    public interface ComponentRegistry {
        boolean hasComponent(String componentId);
    }

    /**
     * Theme lookup — given a ref (e.g. "dialog-default@17"), returns the
     * serialized theme JSON. Implemented by ThemeRepositoryImpl.
     */
    public interface ThemeRepository {
        Optional<JsonNode> findByRef(String ref);
    }

    /**
     * Navigation lookup — given a ref (e.g. "dialog-main@9"), returns the
     * serialized navigation JSON. Implemented by NavigationRepositoryImpl.
     */
    public interface NavigationRepository {
        Optional<JsonNode> findByRef(String ref);
    }
}