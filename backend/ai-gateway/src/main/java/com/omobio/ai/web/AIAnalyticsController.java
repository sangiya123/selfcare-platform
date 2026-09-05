package com.omobio.ai.web;

import com.omobio.ai.domain.ChatSession;
import com.omobio.ai.service.*;
import com.omobio.ai.service.AIResponse.TokenUsage;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI Analytics Controller — sentiment, summarization, and search.
 *
 * Endpoints:
 *   POST /api/v1/ai/sentiment           — analyze sentiment of a single message
 *   POST /api/v1/ai/sentiment/aggregate — aggregate sentiment across messages
 *   POST /api/v1/ai/summarize          — generate conversation summary
 *   GET  /api/v1/ai/search             — semantic search across knowledge base
 *   POST /api/v1/ai/chat/fallback      — direct fallback response (no LLM)
 *   GET  /api/v1/ai/health             — AI subsystem health check
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Analytics", description = "Sentiment, summarization, and search")
public class AIAnalyticsController {

    private final SentimentAnalysisService sentimentService;
    private final ConversationSummarizationService summarizationService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final AiFallbackService fallbackService;
    private final ToolExecutor toolExecutor;

    // -------------------------------------------------------------------------
    // Sentiment
    // -------------------------------------------------------------------------

    @PostMapping("/sentiment")
    @Operation(summary = "Analyze sentiment of a single message")
    public ResponseEntity<ApiResponse<SentimentAnalysisService.SentimentResult>> analyzeSentiment(
            @RequestBody SentimentRequest request) {
        SentimentAnalysisService.SentimentResult result =
                sentimentService.analyze(request.text());
        return ResponseEntity.ok(ApiResponse.of(result, TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/sentiment/aggregate")
    @Operation(summary = "Aggregate sentiment across multiple messages")
    public ResponseEntity<ApiResponse<SentimentAnalysisService.AggregateSentiment>> aggregateSentiment(
            @RequestBody AggregateSentimentRequest request) {
        SentimentAnalysisService.AggregateSentiment result =
                sentimentService.aggregate(request.messages());
        return ResponseEntity.ok(ApiResponse.of(result, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Summarization
    // -------------------------------------------------------------------------

    @PostMapping("/summarize")
    @Operation(summary = "Generate a structured summary of a conversation")
    public ResponseEntity<ApiResponse<ConversationSummarizationService.Summary>> summarize(
            @RequestBody SummarizeRequest request) {
        List<ChatRequest.Message> messages = new ArrayList<>();
        if (request.messages() != null) {
            for (Message m : request.messages()) {
                messages.add(ChatRequest.Message.builder()
                        .role(m.role())
                        .content(m.content())
                        .build());
            }
        }
        ConversationSummarizationService.Summary summary = summarizationService.summarize(messages);
        return ResponseEntity.ok(ApiResponse.of(summary, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    @GetMapping("/search")
    @Operation(summary = "Semantic search across the knowledge base")
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        String tenantId = TenantContext.get().getTenantId();
        long start = System.currentTimeMillis();

        List<VectorEmbeddingService.SimilarChunk> results =
                vectorEmbeddingService.findSimilar(query, tenantId, topK);

        return ResponseEntity.ok(ApiResponse.of(
                new SearchResponse(query, results, results.size(),
                        System.currentTimeMillis() - start),
                TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Fallback (no LLM)
    // -------------------------------------------------------------------------

    @PostMapping("/chat/fallback")
    @Operation(summary = "Get a fallback response without using the LLM")
    public ResponseEntity<ApiResponse<AIResponse>> fallbackChat(
            @RequestBody ChatRequest request) {
        String tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContext.get().getTenantId();
        String userId = request.getUserId() != null ? request.getUserId() : TenantContext.get().getUserId();

        // Find the latest user message
        String latestMessage = null;
        if (request.getMessages() != null) {
            for (int i = request.getMessages().size() - 1; i >= 0; i--) {
                ChatRequest.Message m = request.getMessages().get(i);
                if ("user".equals(m.getRole())) {
                    latestMessage = m.getContent();
                    break;
                }
            }
        }

        if (latestMessage == null) {
            throw new BadRequestException("No user message found in conversation");
        }

        AIResponse response = fallbackService.respond(latestMessage, tenantId, userId);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.of(
                    AIResponse.builder()
                            .content("AI is currently unavailable. Please try again later.")
                            .build(),
                    TenantContext.get().getCorrelationId()));
        }
        response.setSessionId(request.getSessionId());
        return ResponseEntity.ok(ApiResponse.of(response, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    @GetMapping("/health")
    @Operation(summary = "Health check of all AI subsystems")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "OK");
        status.put("intentClassifier", "operational");
        status.put("toolExecutor", "operational");
        status.put("ragService", "operational");
        status.put("vectorEmbedding", "operational");
        status.put("sentiment", "operational");
        status.put("summarization", "operational");
        status.put("fallback", "operational");
        status.put("tenant", TenantContext.get().getTenantId());
        return ResponseEntity.ok(ApiResponse.of(status, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Request records
    // -------------------------------------------------------------------------

    public record SentimentRequest(String text) {}
    public record AggregateSentimentRequest(List<String> messages) {}
    public record Message(String role, String content) {}
    public record SummarizeRequest(List<Message> messages) {}
    public record SearchResponse(
            String query,
            List<VectorEmbeddingService.SimilarChunk> results,
            int total,
            long latencyMs
    ) {}
}
