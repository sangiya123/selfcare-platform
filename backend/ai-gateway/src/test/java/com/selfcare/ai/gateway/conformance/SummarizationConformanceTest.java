package com.selfcare.ai.gateway.conformance;

import com.selfcare.ai.service.ChatRequest;
import com.selfcare.ai.service.ConversationSummarizationService;
import com.selfcare.ai.service.SentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Conformance tests for conversation summarization — verifies the
 * structured summary contract:
 *   1. Required fields: headline, dominantIntent, topics, sentiment, resolutionStatus
 *   2. Topics are deduplicated
 *   3. resolutionStatus ∈ {RESOLVED, UNRESOLVED, PENDING, NO_CONVERSATION}
 *   4. userMessageCount and assistantMessageCount are accurate
 */
@DisplayName("AI Gateway: Conversation Summarization Conformance")
class SummarizationConformanceTest {

    private ConversationSummarizationService service;

    @BeforeEach
    void setUp() {
        SentimentAnalysisService sentimentService = new SentimentAnalysisService();
        service = new ConversationSummarizationService(sentimentService);
    }

    @Test
    @DisplayName("Summary has required fields")
    void hasRequiredFields() {
        var summary = service.summarize(sampleConversation());
        assertThat(summary.headline()).isNotNull();
        assertThat(summary.dominantIntent()).isNotBlank();
        assertThat(summary.topics()).isNotNull();
        assertThat(summary.sentiment()).isNotBlank();
        assertThat(summary.resolutionStatus()).isNotBlank();
    }

    @Test
    @DisplayName("Topics are deduplicated")
    void topicsDeduplicated() {
        var summary = service.summarize(List.of(
                ChatRequest.Message.builder().role("user").content("My bill is wrong, billing issue").build(),
                ChatRequest.Message.builder().role("user").content("I have a billing problem, the bill is incorrect").build()
        ));
        assertThat(summary.topics()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Resolution status is one of allowed values")
    void resolutionStatusInAllowedSet() {
        var summary = service.summarize(sampleConversation());
        assertThat(summary.resolutionStatus()).isIn(
                "RESOLVED", "UNRESOLVED", "PENDING");
    }

    @Test
    @DisplayName("Empty conversation produces minimal summary without error")
    void emptyConversationSafe() {
        var summary = service.summarize(List.of());
        assertThat(summary).isNotNull();
        assertThat(summary.topics()).isEmpty();
        assertThat(summary.dominantIntent()).isEqualTo("NO_CONVERSATION");
    }

    @Test
    @DisplayName("Message counts are accurate")
    void messageCountsAccurate() {
        var summary = service.summarize(List.of(
                ChatRequest.Message.builder().role("user").content("Hi").build(),
                ChatRequest.Message.builder().role("assistant").content("Hello!").build(),
                ChatRequest.Message.builder().role("user").content("Check my balance please").build(),
                ChatRequest.Message.builder().role("assistant").content("Your balance is Rs 250").build()
        ));
        assertThat(summary.userMessageCount()).isEqualTo(2);
        assertThat(summary.assistantMessageCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Balance inquiry intent detection")
    void balanceInquiryIntent() {
        var summary = service.summarize(List.of(
                ChatRequest.Message.builder().role("user").content("What is my balance?").build(),
                ChatRequest.Message.builder().role("assistant").content("Your balance is Rs 250").build()
        ));
        assertThat(summary.dominantIntent()).isEqualTo("BALANCE_INQUIRY");
    }

    private List<ChatRequest.Message> sampleConversation() {
        return List.of(
                ChatRequest.Message.builder().role("user").content("I want to check my data balance").build(),
                ChatRequest.Message.builder().role("assistant").content("Your current balance is 5GB remaining").build(),
                ChatRequest.Message.builder().role("user").content("Great, thanks for the help").build()
        );
    }
}
