package com.omobio.ai.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Chat request sent to the AI gateway.
 *
 * Encapsulates everything needed to run a chat turn:
 * - conversation history (messages)
 * - system prompt override (optional)
 * - tool definitions the AI may call
 * - streaming flag
 * - session ID for context continuity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** Conversation history — each message has a role and content. */
    private List<Message> messages;

    /** Override for the system prompt (optional). */
    private String systemPrompt;

    /**
     * Tool definitions available to the model.
     * Each tool describes a callable action the AI may request.
     */
    private List<ToolDefinition> tools;

    /** Tenant ID for rate limiting and logging. */
    private String tenantId;

    /** User ID for personalization and logging. */
    private String userId;

    /** Session ID to maintain conversation context across turns. */
    private String sessionId;

    /**
     * Whether to stream the response.
     * If true, the response should be delivered as a stream of chunks.
     */
    private boolean streaming;

    /** Temperature for response generation (0.0–2.0). Defaults to 0.7. */
    private Double temperature;

    /** Maximum tokens in the response. Defaults to provider default. */
    private Integer maxTokens;

    /**
     * Additional provider-specific parameters.
     * (e.g., topP, topK, system fingerprint for OpenAI)
     */
    private Map<String, Object> extraParams;

    /**
     * A single message in the conversation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /** Role: system, user, assistant, or tool */
        private String role;
        /** Text content of the message. */
        private String content;
        /** Tool call details, if role=assistant and a tool was called. */
        private ToolCall toolCall;
    }

    /**
     * A tool call made by the model.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        /** Tool name from the tool definitions. */
        private String name;
        /** JSON arguments passed to the tool. */
        private String arguments;
    }

    /**
     * Tool definition — describes a callable action.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        private String name;
        private String description;
        /** JSON Schema describing the tool's input parameters. */
        private Map<String, Object> inputSchema;
    }
}
