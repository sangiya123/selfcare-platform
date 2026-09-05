package com.omobio.platform.common.adapter;

import java.util.Map;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Canonical interface for AI model completions, embeddings, and RAG.
 * Implementations vary per AI provider (OpenAI, Anthropic, Azure OpenAI, etc.).
 * Security is enforced via {@code AiGateway}'s tool-permission layer — the
 * provider adapter itself MUST NOT bypass or override those permissions.
 *
 * <p>AI calls are audit-logged with {@code action=AI_COMPLETION} by {@code ai-gateway}.
 *
 * @see com.omobio.ai.gateway.service.AiGatewayService
 * @see com.omobio.ai.gateway.service.AiGovernanceService
 */
public interface AiProvider extends ApiAdapter {

    // ─── Chat Completions ───────────────────────────────────────────────────

    /**
     * Synchronous (blocking) chat completion.
     *
     * @param tenantId     the tenant performing the call
     * @param model        model identifier, e.g. {@code gpt-4o}, {@code claude-3-5-sonnet}
     * @param messages     ordered list of messages
     * @param tools        available tool definitions (may be empty)
     * @param maxTokens    max tokens in response (0 = provider default)
     * @param temperature  sampling temperature (0–2)
     * @param metadata     extra provider-specific params
     * @return             plain text or tool-call delta stream
     */
    AiResponse chatCompletion(
            String tenantId,
            String model,
            List<AiMessage> messages,
            List<AiTool> tools,
            int maxTokens,
            double temperature,
            Map<String, Object> metadata
    );

    /**
     * Streaming chat completion — yields partial deltas.
     *
     * @param tenantId  the tenant performing the call
     * @param model     model identifier
     * @param messages  ordered list of messages
     * @param tools     available tool definitions (may be empty)
     * @param metadata  extra provider-specific params
     * @return          stream of incremental deltas
     */
    Flux<AiDelta> streamChatCompletion(
            String tenantId,
            String model,
            List<AiMessage> messages,
            List<AiTool> tools,
            Map<String, Object> metadata
    );

    // ─── Embeddings ───────────────────────────────────────────────────────

    /**
     * Generate dense vector embeddings for a text or list of texts.
     *
     * @param tenantId   the tenant performing the call
     * @param texts      one or more input strings
     * @param model      embedding model, e.g. {@code text-embedding-3-small}
     * @return           embedding vectors
     */
    AiEmbeddingsResponse embeddings(
            String tenantId,
            List<String> texts,
            String model
    );

    // ─── RAG ─────────────────────────────────────────────────────────────

    /**
     * Retrieve the top-K document chunks most relevant to the query.
     * The vector search is performed by the AI provider's vector store
     * (or a downstream RAG service). PII MUST be redacted before storage.
     *
     * @param tenantId  the tenant
     * @param query     search query
     * @param topK      number of chunks to return
     * @return          ranked list of chunks with scores
     */
    List<RagChunk> ragRetrieve(
            String tenantId,
            String query,
            int topK
    );

    // ─── Tool-Call Execution ─────────────────────────────────────────────

    /**
     * Execute a tool that was called by the model.
     * Implementations delegate to the appropriate platform service
     * (balance, billing, usage, etc.).  The tool-permission check
     * has already been performed by {@code AiGovernanceService}.
     *
     * @param tenantId    the tenant
     * @param toolCall     the tool-call from the model
     * @return             the result to return to the model
     */
    Mono<AiToolResult> executeToolCall(
            String tenantId,
            AiToolCall toolCall
    );

    // ─── Inner types ─────────────────────────────────────────────────────

    /**
     * Chat message. {@code toolCallId} is non-null only for role=tool.
     */
    record AiMessage(
            String role,        // system | user | assistant | tool
            String content,
            String toolCallId   // present only when role=tool
    ) {}

    record AiTool(
            String type,   // function
            String name,
            String description,
            Map<String, Object> parameters  // JSON Schema
    ) {}

    record AiToolCall(
            String id,
            String name,
            Map<String, Object> arguments
    ) {}

    record AiToolResult(
            String toolCallId,
            String content  // JSON string of result
    ) {}

    record AiResponse(
            String content,
            String finishReason,  // stop | length | tool_calls | content_filter
            AiUsage usage,
            String systemFingerprint
    ) {}

    record AiDelta(
            String content,
            String role,
            AiToolCall toolCall,  // null for non-tool deltas
            String finishReason
    ) {}

    record AiUsage(
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {}

    record AiEmbeddingsResponse(
            List<List<Double>> embeddings,  // one vector per input text
            String model,
            AiUsage usage
    ) {}

    record RagChunk(
            String documentId,
            String chunkId,
            String content,
            double score,
            Map<String, String> metadata  // source, page, etc.
    ) {}
}
