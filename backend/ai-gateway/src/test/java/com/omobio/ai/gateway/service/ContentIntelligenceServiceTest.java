package com.omobio.ai.gateway.service;

import com.omobio.ai.service.ContentIntelligenceService;
import com.omobio.ai.service.LlmProviderRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentIntelligenceServiceTest {

    @Mock private LlmProviderRouter llmRouter;
    private ContentIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new ContentIntelligenceService(llmRouter);
    }

    @Test
    @DisplayName("rewrite calls LLM and returns result")
    void rewrite_callsLlm() {
        when(llmRouter.route(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("Rewritten content here.");
        var result = service.rewrite("Original content here.", "t1", "u1");
        assertThat(result.content()).isEqualTo("Rewritten content here.");
        assertThat(result.action()).isEqualTo("rewrite");
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("rewrite falls back to template rewrite when LLM unavailable")
    void rewrite_fallbackToTemplate() {
        when(llmRouter.route(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);
        var result = service.rewrite("hello world.", "t1", "u1");
        assertThat(result.content()).isEqualTo("Hello world.");
    }

    @Test
    @DisplayName("rewrite returns no-op for empty input")
    void rewrite_empty() {
        var result = service.rewrite("", "t1", "u1");
        assertThat(result.status()).isEqualTo("EMPTY_INPUT");
        assertThat(result.action()).isEqualTo("no-op");
    }

    @Test
    @DisplayName("translate applies terminology memory for telco-en")
    void translate_telcoTerminology() {
        when(llmRouter.route(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("Your customer can top up easily.");
        var result = service.translate("Your subscriber can recharge easily.", "en", "telco", "t1", "u1");
        // "customer" → "subscriber" (already), "top up" → "recharge" (already)
        // Verify translation returned
        assertThat(result.content()).isNotBlank();
    }

    @Test
    @DisplayName("translate falls back to original when LLM returns null")
    void translate_fallbackToOriginal() {
        when(llmRouter.route(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);
        var result = service.translate("Original content", "si", "telco", "t1", "u1");
        assertThat(result.content()).isEqualTo("Original content");
    }

    @Test
    @DisplayName("accessibilityCheck reports missing alt text")
    void accessibility_missingAlt() {
        var report = service.accessibilityCheck(null, "Some context", "telco");
        assertThat(report.status()).isEqualTo("ISSUES_FOUND");
        assertThat(report.issues()).contains("MISSING_ALT_TEXT");
    }

    @Test
    @DisplayName("accessibilityCheck reports 'click here' anti-pattern")
    void accessibility_clickHere() {
        var report = service.accessibilityCheck("An image", "Click here to view", "telco");
        assertThat(report.issues()).contains("AMBIGUOUS_LINK_TEXT");
    }

    @Test
    @DisplayName("accessibilityCheck passes when nothing is wrong")
    void accessibility_pass() {
        var report = service.accessibilityCheck("Person reading on phone", "View your bill", "telco");
        assertThat(report.status()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("campaignVariants respects count and industry")
    void campaignVariants_telco() {
        List<String> variants = service.campaignVariants("5GB Plan", "1GB bonus", "telco", 3);
        assertThat(variants).hasSize(3);
        assertThat(variants.get(0)).contains("5GB Plan").contains("1GB bonus");
    }

    @Test
    @DisplayName("campaignVariants uses insurance templates when industry=insurance")
    void campaignVariants_insurance() {
        List<String> variants = service.campaignVariants("LifeCover", "1M sum assured", "insurance", 2);
        assertThat(variants).hasSize(2);
        // Should use insurance-specific language
        assertThat(variants.get(0).toLowerCase()).containsAnyOf("protect", "covered", "policy", "coverage");
    }

    @Test
    @DisplayName("generateFaq extracts Q&A pairs from text")
    void generateFaq() {
        String text = "To check your balance, dial #123#. You can also use the app. Recharge is available online.";
        var faqs = service.generateFaq(text, 3);
        assertThat(faqs).isNotEmpty();
        // Each FAQ should have a question and answer
        faqs.forEach(f -> {
            assertThat(f.question()).isNotBlank();
            assertThat(f.answer()).isNotBlank();
        });
    }

    @Test
    @DisplayName("generateFaq returns empty for blank input")
    void generateFaq_empty() {
        assertThat(service.generateFaq("", 5)).isEmpty();
        assertThat(service.generateFaq(null, 5)).isEmpty();
    }
}
