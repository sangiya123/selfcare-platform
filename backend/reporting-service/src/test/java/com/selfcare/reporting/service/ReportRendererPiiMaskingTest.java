package com.selfcare.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PII masking in {@link ReportRenderer}.
 *
 * Verifies that:
 * - Column-name based detection fully redacts sensitive columns
 * - Partial mask preserves last 4 chars
 * - Inline pattern detection catches PII in arbitrary columns
 * - Masking can be disabled via configuration
 */
class ReportRendererPiiMaskingTest {

    private ReportRenderer renderer;
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        renderer = new ReportRenderer(new ObjectMapper());
        outputDir = Files.createTempDirectory("report-test-");
        ReflectionTestUtils.setField(renderer, "outputDir", outputDir.toString());
        ReflectionTestUtils.setField(renderer, "csvSeparator", ",");
        ReflectionTestUtils.setField(renderer, "maxRows", 1000L);
        ReflectionTestUtils.setField(renderer, "piiMaskEnabled", true);
    }

    @Test
    @DisplayName("Full redacts password/secret/token columns")
    void fullRedactsSensitiveColumns() throws IOException {
        com.selfcare.reporting.domain.ReportDefinition def = new com.selfcare.reporting.domain.ReportDefinition();
        def.setName("users");
        def.setOutputFormat("CSV");
        com.selfcare.reporting.domain.ReportExecution exec = new com.selfcare.reporting.domain.ReportExecution();
        exec.setId("exec-1");
        exec.setTenantId("test-tenant");

        renderer.render(exec, def, List.of(
                Map.of("userId", "u-1", "password", "supersecret", "apiKey", "sk-12345")
        ));
        Path rendered = Files.list(outputDir.resolve("test-tenant"))
                .filter(p -> p.toString().contains("exec-1"))
                .findFirst().orElseThrow();
        String content = Files.readString(rendered);
        assertThat(content).contains("***");
        assertThat(content).doesNotContain("supersecret");
        assertThat(content).doesNotContain("sk-12345");
    }

    @Test
    @DisplayName("Partially masks msisdn/email columns (last 4 visible)")
    void partiallyMasks() throws IOException {
        com.selfcare.reporting.domain.ReportDefinition def = new com.selfcare.reporting.domain.ReportDefinition();
        def.setName("users");
        def.setOutputFormat("CSV");
        com.selfcare.reporting.domain.ReportExecution exec = new com.selfcare.reporting.domain.ReportExecution();
        exec.setId("exec-2");
        exec.setTenantId("test-tenant");

        renderer.render(exec, def, List.of(
                Map.of("msisdn", "+94771234567", "email", "user@example.com")
        ));
        Path rendered = Files.list(outputDir.resolve("test-tenant"))
                .filter(p -> p.toString().contains("exec-2"))
                .findFirst().orElseThrow();
        String content = Files.readString(rendered);
        assertThat(content).contains("****4567");
        assertThat(content).doesNotContain("+94771234567");
        // Email column is partially masked (last 4 visible) via column detection
        assertThat(content).doesNotContain("user@example.com");
        assertThat(content).contains(".com");
    }

    @Test
    @DisplayName("Inline pattern detection catches PII in non-obvious columns")
    void inlinePatternDetection() throws IOException {
        com.selfcare.reporting.domain.ReportDefinition def = new com.selfcare.reporting.domain.ReportDefinition();
        def.setName("logs");
        def.setOutputFormat("CSV");
        com.selfcare.reporting.domain.ReportExecution exec = new com.selfcare.reporting.domain.ReportExecution();
        exec.setId("exec-3");
        exec.setTenantId("test-tenant");

        renderer.render(exec, def, List.of(
                Map.of("notes", "Customer 94771234567 reported issue", "extra", "national ID 123456789V")
        ));
        Path rendered = Files.list(outputDir.resolve("test-tenant"))
                .filter(p -> p.toString().contains("exec-3"))
                .findFirst().orElseThrow();
        String content = Files.readString(rendered);
        // The phone number is partially masked inline (last 4 visible)
        assertThat(content).contains("****4567");
        // The NIC digits are consumed by the inline phone pattern (partial mask)
        assertThat(content).contains("6789V");
        // Neither raw value is ever written
        assertThat(content).doesNotContain("94771234567");
        assertThat(content).doesNotContain("123456789V");
    }

    @Test
    @DisplayName("Masks JSON output")
    void masksJsonOutput() throws IOException {
        com.selfcare.reporting.domain.ReportDefinition def = new com.selfcare.reporting.domain.ReportDefinition();
        def.setName("users");
        def.setOutputFormat("JSON");
        com.selfcare.reporting.domain.ReportExecution exec = new com.selfcare.reporting.domain.ReportExecution();
        exec.setId("exec-4");
        exec.setTenantId("test-tenant");

        renderer.render(exec, def, List.of(
                Map.of("userId", "u-1", "password", "supersecret", "email", "x@example.com")
        ));
        Path rendered = Files.list(outputDir.resolve("test-tenant"))
                .filter(p -> p.toString().contains("exec-4"))
                .findFirst().orElseThrow();
        String content = Files.readString(rendered);
        assertThat(content).contains("***");
        // Email column is partially masked via column detection (last 4 visible)
        assertThat(content).doesNotContain("x@example.com");
        assertThat(content).doesNotContain("user@example.com");
        assertThat(content).doesNotContain("supersecret");
    }

    @Test
    @DisplayName("PII masking can be disabled via configuration")
    void canBeDisabled() throws IOException {
        ReflectionTestUtils.setField(renderer, "piiMaskEnabled", false);
        com.selfcare.reporting.domain.ReportDefinition def = new com.selfcare.reporting.domain.ReportDefinition();
        def.setName("users");
        def.setOutputFormat("CSV");
        com.selfcare.reporting.domain.ReportExecution exec = new com.selfcare.reporting.domain.ReportExecution();
        exec.setId("exec-5");
        exec.setTenantId("test-tenant");

        renderer.render(exec, def, List.of(
                Map.of("password", "supersecret")
        ));
        Path rendered = Files.list(outputDir.resolve("test-tenant"))
                .filter(p -> p.toString().contains("exec-5"))
                .findFirst().orElseThrow();
        String content = Files.readString(rendered);
        assertThat(content).contains("supersecret");
        assertThat(content).doesNotContain("***");
    }
}
