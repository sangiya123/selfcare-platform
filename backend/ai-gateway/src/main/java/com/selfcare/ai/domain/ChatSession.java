package com.selfcare.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat session — persistent record of a conversation with the AI.
 *
 * Stored in MongoDB. Sessions accumulate messages over the lifetime of a
 * customer conversation. Old sessions are automatically expired.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_sessions")
public class ChatSession {

    /** Session ID — also the document _id. */
    @Id
    private String id;

    /** Use case this session originates from (e.g., "customer_chat", "engineering_assistant"). */
    private String useCaseId;

    /** Session start timestamp. */
    private Instant startedAt;

    /** Tenant ID for tenant isolation. */
    @Indexed
    private String tenantId;

    /** User ID for session ownership. */
    @Indexed
    private String userId;

    /** Optional human-friendly title for the session. */
    private String title;

    /** Channel of origin (WEB, MOBILE, WHATSAPP, etc.). */
    private String channel;

    /** List of messages in this session (chronological). */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    /** LLM provider used for the session (e.g., "anthropic", "openai"). */
    private String provider;

    /** Model used (e.g., "claude-sonnet-4-20250514"). */
    private String model;

    /** Session creation timestamp. */
    @Indexed
    private Instant createdAt;

    /** Last activity timestamp. */
    private Instant lastActivityAt;

    /** Session expiry timestamp. */
    @Indexed
    private Instant expiresAt;

    /** Whether the session is active. */
    private boolean active;

    /**
     * Single message in a chat session.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        /** Role: user, assistant, system, tool. */
        private String role;
        /** Message content. */
        private String content;
        /** Tool calls (if any). */
        private List<ToolCall> toolCalls;
        /** Timestamp the message was created. */
        private Instant timestamp;
        /** Token count for this message. */
        private Integer tokenCount;
    }

    /**
     * Tool call reference within a message.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String toolName;
        private String arguments;
        private String result;
        private boolean success;
    }
}
