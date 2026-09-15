package com.selfcare.ai.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.ai.service.LlmProviderRouter;
import com.selfcare.ai.service.StudioCopilotService;
import com.selfcare.ai.service.StudioCopilotService.IntegrationMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudioCopilotServiceTest {

    @Mock private LlmProviderRouter llmRouter;
    private StudioCopilotService service;

    @BeforeEach
    void setUp() {
        service = new StudioCopilotService(llmRouter, new ObjectMapper());
    }

    @Test
    @DisplayName("generatePageConfig returns template when LLM unavailable")
    void pageConfig_templateFallback() {
        when(llmRouter.route(anyString(), anyString(), any(), any())).thenReturn(null);
        Map<String, Object> page = service.generatePageConfig("Show my balance and bills", "telco");
        assertThat(page).containsKey("sections");
        assertThat(page.get("status")).isEqualTo("DRAFT");
        assertThat(page.get("requiresApproval")).isEqualTo(true);
    }

    @Test
    @DisplayName("generatePageConfig uses LLM output when valid JSON returned")
    void pageConfig_llmJson() {
        when(llmRouter.route(anyString(), anyString(), any(), any()))
                .thenReturn("{\"id\":\"page-1\",\"sections\":[]}");
        Map<String, Object> page = service.generatePageConfig("Generic page", "telco");
        assertThat(page.get("source")).isEqualTo("llm");
    }

    @Test
    @DisplayName("generatePageConfig returns error for empty requirement")
    void pageConfig_emptyRequirement() {
        Map<String, Object> page = service.generatePageConfig("", "telco");
        assertThat(page).containsKey("error");
    }

    @Test
    @DisplayName("recommendLayoutVariants returns sensible defaults for home page")
    void layoutVariants_home() {
        var variants = service.recommendLayoutVariants("home", "telco");
        assertThat(variants).isNotEmpty();
        assertThat(variants.get(0).id()).isEqualTo("balanced");
    }

    @Test
    @DisplayName("generateVisibilityRule detects postpaid + balance")
    void visibilityRule_postpaidBalance() {
        String rule = service.generateVisibilityRule("Show to postpaid users with non-zero balance");
        assertThat(rule).contains("POSTPAID");
    }

    @Test
    @DisplayName("generateVisibilityRule detects churn risk")
    void visibilityRule_churn() {
        String rule = service.generateVisibilityRule("Hide for churn risk users");
        assertThat(rule).contains("churnRiskLevel");
    }

    @Test
    @DisplayName("generateJourneyDraft creates step count steps")
    void journeyDraft() {
        var journey = service.generateJourneyDraft("Pay a bill", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) journey.get("steps");
        assertThat(steps).hasSize(3);
        assertThat(journey.get("status")).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("suggestIntegrationMapping maps common fields to canonical")
    void integrationMapping_common() {
        Map<String, Object> payload = Map.of(
                "msisdn", "94771234567",
                "balance", 250.0,
                "currency", "LKR",
                "name", "John Doe"
        );
        IntegrationMapping mapping = service.suggestIntegrationMapping(payload);
        assertThat(mapping.fieldMappings()).containsEntry("msisdn", "connectionId");
        assertThat(mapping.fieldMappings()).containsEntry("balance", "balance.amount");
        assertThat(mapping.fieldMappings()).containsEntry("currency", "balance.currency");
    }

    @Test
    @DisplayName("suggestIntegrationMapping returns EMPTY_INPUT for empty payload")
    void integrationMapping_empty() {
        IntegrationMapping m = service.suggestIntegrationMapping(Map.of());
        assertThat(m.status()).isEqualTo("EMPTY_INPUT");
    }

    @Test
    @DisplayName("explainValidationIssues turns 'required' into explanation")
    void explainValidation_required() {
        var explanations = service.explainValidationIssues(List.of(
                Map.of("path", "components[0].id", "message", "is required")
        ));
        assertThat(explanations.get(0).explanation()).contains("required");
    }

    @Test
    @DisplayName("explainMigration flags direct MySQL access")
    void explainMigration_mysql() {
        String notes = service.explainMigration("<?php $r = mysql_query('SELECT * FROM users');", "BalanceProvider");
        assertThat(notes).contains("MySQL");
    }

    @Test
    @DisplayName("generateReleaseNotes formats with version and changes")
    void releaseNotes() {
        String notes = service.generateReleaseNotes(List.of("Add allowance expiry", "Fix bill PDF"), "1.2.0");
        assertThat(notes).contains("# Release 1.2.0");
        assertThat(notes).contains("Add allowance expiry");
        assertThat(notes).contains("Operator impact");
    }
}
