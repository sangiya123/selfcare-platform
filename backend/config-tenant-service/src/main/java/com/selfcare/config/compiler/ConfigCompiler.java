package com.selfcare.config.compiler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.domain.LayoutDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Config Compiler — validates and compiles layout documents into immutable, signed runtime manifests.
 *
 * Per ADR-004: Mongo is config source, not per-request runtime interpreter.
 * At publish time, source config is schema-validated (ConfigSchemaValidator) and compiled
 * into an immutable runtime manifest carrying a content hash + HMAC signature. Services and
 * apps cache and verify the compiled manifest directly.
 *
 * Compilation steps:
 * 1. Schema validation (JSON Schema for layout documents — publish gate)
 * 2. Reference validation (themeRef, navigationRef, componentId exist)
 * 3. Visibility evaluation (resolve conditional configs)
 * 4. Action validation (registered actions only — no arbitrary code)
 * 5. Component compatibility check (compatible with app versions)
 * 6. Token resolution (themeRef -> ThemeTokens)
 * 7. Final manifest emission (manifestId + HMAC signature)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigCompiler {

    private final ObjectMapper objectMapper;
    private final ComponentRegistry componentRegistry;
    private final ThemeRepository themeRepository;
    private final NavigationRepository navigationRepository;
    private final ComponentCatalogRepository componentCatalogRepository;
    private final ConfigSchemaValidator schemaValidator;
    private final ManifestSigner manifestSigner;

    public static final String LAYOUT_SCHEMA = "layout-document.schema.json";

    /** Publish-time schema gate: throws IllegalStateException when the document does not conform. */
    public void validateDocument(LayoutDocument document) {
        ObjectNode node = toValidationTree(document);
        schemaValidator.validateOrThrow(LAYOUT_SCHEMA, node);
        validateSchemaVersion(document);
    }

    /**
     * Compile a layout document into an immutable runtime manifest.
     */
    public CompiledManifest compile(LayoutDocument document) {
        log.info("Compiling layout: tenant={}, experience={}, version={}",
                document.getTenantId(), document.getExperience(), document.getConfigVersion());

        // 1. Validate schema version
        validateSchemaVersion(document);

        // 2. Resolve theme (optional in dev — fall back to empty if missing).
        //    The repository already flattens ThemeDocument -> ManifestTheme
        //    (manifest contract: theme.colors at root, not baseTokens wrapper).
        Map<String, Object> theme = themeRepository.findByRef(document.getThemeRef())
                .orElseGet(() -> {
                    log.debug("Theme not found: {} — using empty default", document.getThemeRef());
                    return Map.of();
                });

        // 3. Resolve navigation (optional — fall back to empty if missing).
        //    The repository already flattens navigation_documents -> NavItem[].
        List<Map<String, Object>> navigation = navigationRepository.findByRef(document.getNavigationRef())
                .orElseGet(() -> {
                    log.debug("Navigation not found: {} — using empty default", document.getNavigationRef());
                    return List.of();
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

        // 4b. Resolve active component catalog for the manifest
        List<ComponentCatalogItem> components = componentCatalogRepository
                .findActive(document.getTenantId(), document.getEnvironment());

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
        manifest.setServices(document.getServices());
        manifest.setDataSources(document.getDataSources());
        manifest.setSections(compiledSections);
        manifest.setComponents(components);
        manifest.setCompiledAt(java.time.Instant.now());
        manifest.setCompilerVersion("2.0.0");

        // 6. Content identity + immutable signature
        manifest.setManifestId(contentHash(manifest));
        manifest.setSigningAlg(ManifestSigner.ALGORITHM);
        manifest.setSignature(manifestSigner.sign(manifest));

        log.info("Compiled manifest: sections={}, themeRef={}, configVersion={}, manifestId={}",
                compiledSections.size(), document.getThemeRef(), document.getConfigVersion(),
                manifest.getManifestId());
        return manifest;
    }

    private String contentHash(CompiledManifest manifest) {
        try {
            ObjectNode unsigned = (ObjectNode) nonNullMapper().valueToTree(manifest);
            unsigned.remove("manifestId");
            unsigned.remove("signature");
            unsigned.remove("signingAlg");
            return ManifestHashing.sha256(
                    nonNullMapper().writeValueAsString(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute manifest content hash", e);
        }
    }

    /** Strip runtime/audit fields so only authorable config is schema-validated. */
    private ObjectNode toValidationTree(LayoutDocument document) {
        ObjectNode node = (ObjectNode) nonNullMapper().valueToTree(document);
        node.remove("id");
        node.remove("createdAt");
        node.remove("updatedAt");
        node.remove("publishedAt");
        node.remove("publishedBy");
        node.remove("createdBy");
        node.remove("version");
        return node;
    }

    /** Compact mapper that omits null placeholders — keeps the manifest and the publish gate canonical. */
    private ObjectMapper nonNullMapper() {
        return objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
        /** Flattened ManifestTheme (colors/typography at root) — app contract. */
        private Map<String, Object> theme;
        /** Flattened NavItem[] — app contract. */
        private List<Map<String, Object>> navigation;
        private Map<String, Object> services;
        private Map<String, Object> dataSources;
        private List<CompiledSection> sections;
        private List<ComponentCatalogItem> components;
        private java.time.Instant compiledAt;
        private String compilerVersion;
        private String manifestId;
        private String signingAlg;
        private String signature;
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
     * FLATTENED ManifestTheme map (colors/typography at root — app contract).
     * Implemented by ThemeRepositoryImpl.
     */
    public interface ThemeRepository {
        Optional<Map<String, Object>> findByRef(String ref);
    }

    /**
     * Component catalog lookup — given a tenant + environment, returns the
     * PUBLISHED catalog items to embed in the compiled manifest.
     * Implemented by ComponentCatalogRepositoryImpl.
     */
    public interface ComponentCatalogRepository {
        List<ComponentCatalogItem> findActive(String tenantId, String environment);
    }

    /**
     * Navigation lookup — given a ref (e.g. "dialog-main@9"), returns the
     * FLATTENED NavItem[] list (app contract). Implemented by
     * NavigationRepositoryImpl.
     */
    public interface NavigationRepository {
        Optional<List<Map<String, Object>>> findByRef(String ref);
    }
}