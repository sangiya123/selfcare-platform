package com.omobio.ai.service;

import com.omobio.ai.service.AIResponse.TokenUsage;
import com.omobio.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streaming Service — manages Server-Sent Events (SSE) streaming for AI responses.
 *
 * Architecture:
 * 1. Client opens a streaming chat request → server assigns a streamId
 * 2. LLM provider yields chunks → pushed to the sink for that streamId
 * 3. Client reads from the SSE endpoint, each event is a JSON chunk
 * 4. Stream completes when LLM finishes or an error occurs
 * 5. SSE endpoint sends final event with full response + token usage
 *
 * SSE event types:
 *   chunk     — partial text token (data: {"content": "Hello", "streamId": "..."})
 *   tool_call — tool call requested (data: {"toolName": "get_balance", ...})
 *   done      — final event (data: {"content": "...", "tokenUsage": {...}})
 *   error     — error event (data: {"error": "..."})
 *
 * Redis is used for multi-instance coordination. In production, consider
 * Redis pub/sub or a message queue for horizontal scaling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingService {

    private final RedisTemplate<String, Object> redisTemplate;

    // In-memory sinks (per-instance). In production: Redis pub/sub + local fallback.
    private final Map<String, Sinks.Many<String>> localSinks = new ConcurrentHashMap<>();
    private static final String STREAM_PREFIX = "omobio:ai:stream:";
    private static final Duration STREAM_TTL = Duration.ofMinutes(10);

    // -------------------------------------------------------------------------
    // Stream lifecycle
    // -------------------------------------------------------------------------

    /**
     * Create a new stream and return its ID.
     */
    public String createStream(String sessionId, String tenantId) {
        String streamId = UUID.randomUUID().toString();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(1000);
        localSinks.put(streamId, sink);

        // Store metadata in Redis for cross-instance discovery
        redisTemplate.opsForHash().putAll(STREAM_PREFIX + "meta:" + streamId, Map.of(
                "sessionId", sessionId != null ? sessionId : "",
                "tenantId", tenantId,
                "createdAt", String.valueOf(System.currentTimeMillis()),
                "status", "ACTIVE"
        ));
        redisTemplate.expire(STREAM_PREFIX + "meta:" + streamId, STREAM_TTL);

        log.info("Stream created: streamId={}, sessionId={}", streamId, sessionId);
        return streamId;
    }

    /**
     * Push a text chunk to a stream.
     */
    public void pushChunk(String streamId, String content) {
        Sinks.Many<String> sink = localSinks.get(streamId);
        if (sink == null) {
            log.warn("Sink not found for stream: {}", streamId);
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(toSseEvent("chunk",
                Map.of("content", content, "streamId", streamId)));
        if (result.isFailure()) {
            log.warn("Failed to push chunk to stream {}: {}", streamId, result);
        }
    }

    /**
     * Push a tool call event to a stream.
     */
    public void pushToolCall(String streamId, String toolName, String arguments) {
        Sinks.Many<String> sink = localSinks.get(streamId);
        if (sink == null) return;
        sink.tryEmitNext(toSseEvent("tool_call", Map.of(
                "toolName", toolName,
                "arguments", arguments,
                "streamId", streamId
        )));
    }

    /**
     * Complete a stream with the final response.
     */
    public void completeStream(String streamId, String fullContent,
                               TokenUsage tokenUsage, String provider, String model) {
        Sinks.Many<String> sink = localSinks.get(streamId);
        if (sink == null) return;

        Map<String, Object> doneData = new java.util.HashMap<>();
        doneData.put("content", fullContent);
        doneData.put("streamId", streamId);
        doneData.put("provider", provider);
        doneData.put("model", model);
        if (tokenUsage != null) {
            doneData.put("tokenUsage", Map.of(
                    "promptTokens", tokenUsage.getPromptTokens(),
                    "completionTokens", tokenUsage.getCompletionTokens(),
                    "totalTokens", tokenUsage.getTotalTokens()
            ));
        }

        sink.tryEmitNext(toSseEvent("done", doneData));
        sink.tryEmitComplete();

        localSinks.remove(streamId);
        redisTemplate.opsForHash().put(STREAM_PREFIX + "meta:" + streamId, "status", "COMPLETED");
        log.info("Stream completed: streamId={}", streamId);
    }

    /**
     * Emit an error on a stream.
     */
    public void errorStream(String streamId, String error) {
        Sinks.Many<String> sink = localSinks.get(streamId);
        if (sink == null) return;
        sink.tryEmitNext(toSseEvent("error", Map.of("error", error, "streamId", streamId)));
        sink.tryEmitComplete();
        localSinks.remove(streamId);
    }

    // -------------------------------------------------------------------------
    // SSE flux
    // -------------------------------------------------------------------------

    /**
     * Get the SSE flux for a stream. Used by the controller to stream to the client.
     */
    public Flux<String> getFlux(String streamId) {
        Sinks.Many<String> sink = localSinks.get(streamId);
        if (sink == null) {
            return Flux.error(new IllegalStateException("Stream not found: " + streamId));
        }
        return sink.asFlux()
                .takeUntil(s -> s.contains("\"type\":\"done\"") || s.contains("\"type\":\"error\""))
                .concatWith(Flux.just(toSseEvent("close", Map.of("streamId", streamId))));
    }

    /**
     * Get stream metadata from Redis.
     */
    public Map<Object, Object> getStreamMeta(String streamId) {
        return redisTemplate.opsForHash().entries(STREAM_PREFIX + "meta:" + streamId);
    }

    // -------------------------------------------------------------------------
    // SSE helpers
    // -------------------------------------------------------------------------

    /**
     * Format a map as an SSE data line.
     * SSE format: event: <type>\ndata: <json>\n\n
     */
    private String toSseEvent(String type, Map<String, Object> data) {
        return "event: " + type + "\ndata: " + toJson(data) + "\n\n";
    }

    private String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) {
                sb.append("\"").append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else if (v instanceof Number n) {
                sb.append(n);
            } else if (v instanceof Boolean b) {
                sb.append(b);
            } else {
                sb.append("null");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
