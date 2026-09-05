package com.omobio.ai.gateway.service;

import com.omobio.ai.service.EngineeringAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EngineeringAssistantServiceTest {

    private EngineeringAssistantService service;

    @BeforeEach
    void setUp() {
        service = new EngineeringAssistantService(null);
    }

    @Test
    @DisplayName("generateTests always includes happy path and unauthorized")
    void tests_basics() {
        var tests = service.generateTests("public void processPayment()", "...");
        assertThat(tests).isNotEmpty();
        assertThat(tests.stream().anyMatch(t -> t.id().equals("happy_path"))).isTrue();
        assertThat(tests.stream().anyMatch(t -> t.id().equals("unauthorized"))).isTrue();
    }

    @Test
    @DisplayName("generateTests adds tenant_isolation when signature mentions tenant")
    void tests_tenantIsolation() {
        var tests = service.generateTests("public Balance getTenantBalance(String tenantId)", "...");
        assertThat(tests.stream().anyMatch(t -> t.id().equals("tenant_isolation"))).isTrue();
    }

    @Test
    @DisplayName("generateTests adds idempotency for create/process methods")
    void tests_idempotency() {
        var tests = service.generateTests("public void processPayment(...)", "...");
        assertThat(tests.stream().anyMatch(t -> t.id().equals("idempotency"))).isTrue();
    }

    @Test
    @DisplayName("generateMockScenarios covers happy, 4xx, 5xx, rate limit")
    void mockScenarios_full() {
        var scenarios = service.generateMockScenarios("/api/v1/foo", "GET");
        assertThat(scenarios).extracting(EngineeringAssistantService.MockScenario::id)
                .contains("success_200", "validation_400", "unauthorized_401",
                        "rate_limit_429", "upstream_503");
    }

    @Test
    @DisplayName("reviewCode flags System.out.println")
    void review_systemOut() {
        var comments = service.reviewCode("System.out.println(\"hi\");", "java");
        assertThat(comments).isNotEmpty();
        assertThat(comments.stream().anyMatch(c -> c.message().contains("System.out"))).isTrue();
    }

    @Test
    @DisplayName("reviewCode flags possible hardcoded credentials")
    void review_credentials() {
        var comments = service.reviewCode("String password = \"admin123\";", "java");
        assertThat(comments.stream().anyMatch(c -> "error".equals(c.severity()))).isTrue();
    }

    @Test
    @DisplayName("reviewCode flags empty catch blocks")
    void review_emptyCatch() {
        var comments = service.reviewCode("try { x(); } catch (Exception e) {}", "java");
        assertThat(comments.stream().anyMatch(c -> c.message().contains("Empty catch"))).isTrue();
    }

    @Test
    @DisplayName("analyzeDependencyUpgrade flags major version bump")
    void depUpgrade_majorBump() {
        var impact = service.analyzeDependencyUpgrade("spring-boot", "2.7.0", "3.0.0");
        assertThat(impact.breakingChanges()).isNotEmpty();
        assertThat(impact.recommendation()).contains("PLAN");
    }

    @Test
    @DisplayName("analyzeDependencyUpgrade says OK for minor bump")
    void depUpgrade_minor() {
        var impact = service.analyzeDependencyUpgrade("spring-boot", "3.2.0", "3.3.0");
        assertThat(impact.recommendation()).contains("OK");
    }

    @Test
    @DisplayName("analyzeDependencyUpgrade blocks pre-release")
    void depUpgrade_preRelease() {
        var impact = service.analyzeDependencyUpgrade("foo", "1.0.0", "2.0.0-SNAPSHOT");
        assertThat(impact.recommendation()).contains("HOLD");
    }

    @Test
    @DisplayName("clusterFlakyTests groups by test name and computes fail rate")
    void clusterFlaky() {
        Instant now = Instant.now();
        var records = List.of(
                new EngineeringAssistantService.FlakyTestRecord("testA", "FAIL", "Connection refused", now),
                new EngineeringAssistantService.FlakyTestRecord("testA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("testA", "FAIL", "Connection refused", now),
                new EngineeringAssistantService.FlakyTestRecord("testB", "PASS", null, now)
        );
        var clusters = service.clusterFlakyTests(records);
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).testName()).isEqualTo("testA");
        assertThat(clusters.get(0).failRate()).isEqualTo(2.0 / 3.0);
    }

    @Test
    @DisplayName("clusterFlakyTests ignores tests with low fail rate")
    void clusterFlaky_lowFailRate() {
        Instant now = Instant.now();
        var records = List.of(
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "PASS", null, now),
                new EngineeringAssistantService.FlakyTestRecord("stableA", "FAIL", "X", now)
        );
        // 1/10 = 10% — borderline; we use < 0.1 as the cutoff
        var clusters = service.clusterFlakyTests(records);
        assertThat(clusters).isEmpty();
    }
}
