package com.selfcare.ai.gateway.service;

import com.selfcare.ai.service.ChatRequest;
import com.selfcare.ai.service.ConversationSummarizationService;
import com.selfcare.ai.service.SentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ConversationSummarizationServiceTest {

    private ConversationSummarizationService service;

    @BeforeEach
    void setUp() {
        SentimentAnalysisService sentimentService = new SentimentAnalysisService();
        service = new ConversationSummarizationService(sentimentService);
    }

    @Test
    @DisplayName("summarizes a basic conversation")
    void basicSummary() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("What is my balance?").build(),
                ChatRequest.Message.builder().role("assistant").content("Your balance is Rs. 250").build(),
                ChatRequest.Message.builder().role("user").content("Thanks!").build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.headline()).isNotEmpty();
        assertThat(summary.dominantIntent()).isEqualTo("BALANCE_INQUIRY");
        assertThat(summary.userMessageCount()).isEqualTo(2);
        assertThat(summary.assistantMessageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("returns empty summary for empty messages")
    void emptyMessages() {
        var summary = service.summarize(List.of());
        assertThat(summary.headline()).isEmpty();
        assertThat(summary.dominantIntent()).isEqualTo("NO_CONVERSATION");
    }

    @Test
    @DisplayName("detects support intent from complaint")
    void supportIntent() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("I have a complaint about my service").build(),
                ChatRequest.Message.builder().role("assistant").content("I'm sorry to hear").build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.dominantIntent()).isEqualTo("SUPPORT");
    }

    @Test
    @DisplayName("detects insurance intent")
    void insuranceIntent() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("When is my premium due?").build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.dominantIntent()).isEqualTo("INSURANCE");
    }

    @Test
    @DisplayName("extracts action items from tool calls")
    void actionItems() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("Check my balance").build(),
                ChatRequest.Message.builder()
                        .role("assistant")
                        .content("")
                        .toolCall(ChatRequest.ToolCall.builder().name("get_balance").arguments("{}").build())
                        .build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.suggestedFollowUps()).isNotEmpty();
    }

    @Test
    @DisplayName("detects resolved status from positive resolution signals")
    void resolvedStatus() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("My issue is resolved, thanks!").build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.resolutionStatus()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("detects unresolved status from negative signals")
    void unresolvedStatus() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("The issue persists, still not working").build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.resolutionStatus()).isEqualTo("UNRESOLVED");
    }

    @Test
    @DisplayName("extracts topics from frequent words")
    void topics() {
        List<ChatRequest.Message> messages = List.of(
                ChatRequest.Message.builder().role("user").content("recharge my data pack").build(),
                ChatRequest.Message.builder().role("user").content("how to recharge data pack").build(),
                ChatRequest.Message.builder().role("user").content("data pack recommendations").build()
        );
        var summary = service.summarize(messages);
        assertThat(summary.topics()).isNotEmpty();
    }
}
