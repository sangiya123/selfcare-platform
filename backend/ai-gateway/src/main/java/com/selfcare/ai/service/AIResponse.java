package com.selfcare.ai.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI response from the gateway.
 *
 * Wraps the raw LLM output with structured metadata:
 * - intent classification (if requested)
 * - tools used (if any)
 * - citations (for RAG-grounded responses)
 * - session ID (for conversation continuity)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponse {

    /** The textual response from the LLM. */
    private String content;

    /**
     * Intent classification (if AIModelGateway.classifyIntent was used).
     * Null when this is a normal chat response.
     */
    private IntentClassification intent;

    /**
     * List of tools the model requested to call.
     * Empty if no tool calls were made.
     */
    private List<ToolCallResult> toolsUsed;

    /**
     * Citations for RAG-grounded responses.
     * Each citation references a source document and the relevant passage.
     */
    private List<Citation> citations;

    /**
     * Session ID from the request — echoed back for continuity.
     */
    private String sessionId;

    /** Token usage metadata (if available from provider). */
    private TokenUsage tokenUsage;

    /** Whether the response was streamed. */
    private boolean streamed;

    /** Provider that generated this response (e.g., "anthropic", "openai"). */
    private String provider;

    /** Model used (e.g., "claude-sonnet-4-20250514"). */
    private String model;

    /** Latency in milliseconds. */
    private Long latencyMs;

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /**
     * Result of a tool call made by the model.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallResult {
        private String toolName;
        private String arguments;
        private String result;
        /** Whether the tool call succeeded. */
        private boolean success;
        private String error;
    }

    /**
     * Citation from a RAG-grounded response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        /** Source document ID. */
        private String sourceId;
        /** Source type: "faq", "policy", "article", etc. */
        private String sourceType;
        /** Relevant passage text. */
        private String text;
        /** Relevance score (0.0–1.0). */
        private double score;
    }

    /**
     * Token usage breakdown.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
