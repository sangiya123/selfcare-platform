package com.selfcare.config.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.config.compiler.ConfigCompiler.CompiledManifest;
import com.selfcare.config.domain.ComponentCatalogItem;
import com.selfcare.config.domain.LayoutDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCompilerTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManifestSigner signer = new ManifestSigner("unit-test-key");
    private final ManifestVerifier verifier = new ManifestVerifier(signer);

    private ConfigCompiler compiler() {
        ConfigSchemaValidator validator = new ConfigSchemaValidator(mapper);
        ConfigCompiler.ComponentRegistry registry = id -> true;
        ConfigCompiler.ThemeRepository themes = ref -> Optional.of(new java.util.HashMap<>());
        ConfigCompiler.NavigationRepository navs = ref -> Optional.of(new java.util.ArrayList<>());
        ConfigCompiler.ComponentCatalogRepository catalog = (tenantId, env) -> java.util.List.of();
        return new ConfigCompiler(mapper, registry, themes, navs, catalog, validator, signer);
    }

    @Test
    void compiledManifestCarriesContentHashAndSignature() {
        CompiledManifest m = compiler().compile(layout());
        assertNotNull(m.getManifestId());
        assertNotNull(m.getSignature());
        assertEquals(ManifestSigner.ALGORITHM, m.getSigningAlg());
        assertEquals("2.0", m.getSchemaVersion());
    }

    @Test
    void compiledManifestVerifiesAndChangesWithContent() {
        CompilerAndVerifier cv = compilerAndVerifier();
        CompiledManifest m = cv.compiler.compile(layout());
        assertTrue(cv.verifier.verify(m));

        String originalHash = m.getManifestId();
        LayoutDocument changed = layout();
        changed.getSections().get(0).setOrder(7);
        CompiledManifest m2 = cv.compiler.compile(changed);
        assertTrue(cv.verifier.verify(m2));
        assertTrue(!originalHash.equals(m2.getManifestId()));
    }

    @Test
    void compiledManifestCarriesActiveComponentCatalog() {
        ComponentCatalogItem card = ComponentCatalogItem.builder()
                .tenantId("dialog-lk").environment("prod")
                .componentId("BalanceCard").label("Balance Card")
                .category("DISPLAY").security("read").enabled(true)
                .status("PUBLISHED").build();

        ConfigSchemaValidator validator = new ConfigSchemaValidator(mapper);
        ConfigCompiler.ComponentRegistry registry = id -> true;
        ConfigCompiler.ThemeRepository themes = ref -> Optional.of(new java.util.HashMap<>());
        ConfigCompiler.NavigationRepository navs = ref -> Optional.of(new java.util.ArrayList<>());
        ConfigCompiler.ComponentCatalogRepository catalog =
                (tenantId, env) -> List.of(card);
        ConfigCompiler compiler = new ConfigCompiler(mapper, registry, themes, navs,
                catalog, validator, signer);

        CompiledManifest m = compiler.compile(layout());
        assertNotNull(m.getComponents());
        assertEquals(1, m.getComponents().size());
        assertEquals("BalanceCard", m.getComponents().get(0).getComponentId());
        assertTrue(verifier.verify(m));
    }

    @Test
    void compilerVersionBumped() {
        assertEquals("2.0.0", compiler().compile(layout()).getCompilerVersion());
    }

    private LayoutDocument layout() {
        LayoutDocument.Action nav = LayoutDocument.Action.builder()
                .event("tap").type("NAVIGATE").route("/usage/history").build();
        LayoutDocument.Section section = LayoutDocument.Section.builder()
                .id("usage").component("UsageSummary").variant("hero")
                .dataSource("usage.summary")
                .order(1)
                .actions(List.of(nav))
                .build();
        return LayoutDocument.builder()
                .tenantId("dialog-lk")
                .environment("prod")
                .experience("home")
                .profileKey("mobile_prepaid")
                .schemaVersion("2.0")
                .configVersion(1)
                .themeRef("dialog-default@1")
                .navigationRef("dialog-main@1")
                .sections(List.of(section))
                .status("PUBLISHED")
                .build();
    }

    private CompilerAndVerifier compilerAndVerifier() {
        return new CompilerAndVerifier(compiler(), verifier);
    }

    private record CompilerAndVerifier(ConfigCompiler compiler, ManifestVerifier verifier) {
    }
}