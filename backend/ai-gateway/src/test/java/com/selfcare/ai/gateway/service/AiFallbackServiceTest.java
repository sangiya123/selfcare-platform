package com.selfcare.ai.gateway.service;

import com.selfcare.ai.service.AIResponse;
import com.selfcare.ai.service.AiFallbackService;
import com.selfcare.ai.service.RAGService;
import com.selfcare.ai.service.SentimentAnalysisService;
import com.selfcare.ai.service.ToolExecutor;
import com.selfcare.ai.service.VectorEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFallbackServiceTest {

    @Mock private ToolExecutor toolExecutor;
    @Mock private RAGService ragService;
    @Mock private VectorEmbeddingService vectorEmbeddingService;

    private AiFallbackService service;

    @BeforeEach
    void setUp() {
        service = new AiFallbackService(toolExecutor, ragService, vectorEmbeddingService,
                new SentimentAnalysisService());
    }

    @Test
    @DisplayName("returns a greeting response for hi/hello")
    void greeting() {
        AIResponse response = service.respond("hello there", "dialog-lk", "user-1");
        assertThat(response).isNotNull();
        assertThat(response.getContent().toLowerCase()).containsAnyOf("hello", "hi", "help");
    }

    @Test
    @DisplayName("returns a balance template for balance inquiry")
    void balanceInquiry() {
        AIResponse response = service.respond("what is my balance?", "dialog-lk", "user-1");
        assertThat(response).isNotNull();
        assertThat(response.getContent()).containsIgnoringCase("balance");
    }

    @Test
    @DisplayName("returns a recharge template for recharge question")
    void recharge() {
        AIResponse response = service.respond("how do I recharge?", "dialog-lk", "user-1");
        assertThat(response).isNotNull();
        assertThat(response.getContent()).containsIgnoringCase("recharge");
    }

    @Test
    @DisplayName("returns a complaint template for complaint")
    void complaint() {
        AIResponse response = service.respond("I have a problem with my account", "dialog-lk", "user-1");
        assertThat(response).isNotNull();
        assertThat(response.getProvider()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("includes intent classification in response")
    void includesIntent() {
        AIResponse response = service.respond("hi there", "dialog-lk", "user-1");
        assertThat(response).isNotNull();
        assertThat(response.getIntent()).isNotNull();
    }

    @Test
    @DisplayName("provider is marked as 'fallback'")
    void fallbackProvider() {
        AIResponse response = service.respond("hello", "dialog-lk", "user-1");
        assertThat(response).isNotNull();
        assertThat(response.getProvider()).isEqualTo("fallback");
    }
}
