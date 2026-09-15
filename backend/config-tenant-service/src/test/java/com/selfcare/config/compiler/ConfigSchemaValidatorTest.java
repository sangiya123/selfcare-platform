package com.selfcare.config.compiler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.config.domain.LayoutDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigSchemaValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final ConfigSchemaValidator validator = new ConfigSchemaValidator(mapper);

    @Test
    void validLayoutPassesPublishGate() {
        assertDoesNotThrow(() -> validator.validateOrThrow(
                ConfigCompiler.LAYOUT_SCHEMA, mapper.valueToTree(layout())));
    }

    @Test
    void unknownActionTypeFailsPublishGate() {
        LayoutDocument doc = layout();
        LayoutDocument.Action bad = LayoutDocument.Action.builder()
                .type("EXEC_ARBITRARY_CODE")
                .build();
        LayoutDocument.Section section = LayoutDocument.Section.builder()
                .id("hack").component("Text").actions(List.of(bad)).build();
        doc.setSections(List.of(section));

        assertThrows(IllegalStateException.class, () -> validator.validateOrThrow(
                ConfigCompiler.LAYOUT_SCHEMA, mapper.valueToTree(doc)));
    }

    @Test
    void missingRequiredFieldFailsPublishGate() {
        LayoutDocument doc = layout();
        doc.setTenantId(null);
        assertThrows(IllegalStateException.class, () -> validator.validateOrThrow(
                ConfigCompiler.LAYOUT_SCHEMA, mapper.valueToTree(doc)));
    }

    private LayoutDocument layout() {
        LayoutDocument.Action nav = LayoutDocument.Action.builder()
                .event("tap").type("NAVIGATE").route("/usage/history").build();
        LayoutDocument.Section section = LayoutDocument.Section.builder()
                .id("usage").component("UsageSummary").variant("hero")
                .dataSource("usage.summary")
                .order(1)
                .actions(List.of(nav))
                .props(java.util.Map.of("showHistory", true))
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
                .status("DRAFT")
                .build();
    }
}