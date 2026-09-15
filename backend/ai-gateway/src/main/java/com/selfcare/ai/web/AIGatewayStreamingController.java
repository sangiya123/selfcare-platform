package com.selfcare.ai.web;

import com.selfcare.ai.service.*;
import com.selfcare.ai.service.AIResponse.TokenUsage;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AI Gateway Streaming API — Server-Sent Events (SSE) for streaming responses.
 *
 * Flow:
 *   1. POST /api/v1/ai/chat/stream → returns { streamId }
 *   2. GET  /api/v1/ai/chat/stream/{streamId}/events  → SSE stream of chunks
 *   3. Final event has type "done" and includes the complete content + token usage
 *
 * This pattern decouples the request from the response, allowing mobile clients
 * to render text as it arrives. The streamId-based design also supports resume
 * and replay scenarios.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/chat/stream")
@RequiredArgsConstructor
@Tag(name = "AI Streaming", description = "Streaming AI chat via Server-Sent Events")
public class AIGatewayStreamingController {

    private final StreamingService streamingService;
    private final AIModelGateway aiGateway;
    private final ContentModerationService moderationService;
    private final ToolExecutor toolExecutor;
    private final TokenUsageService tokenUsageService;

    /**
     * Initiate a streaming chat. Returns a streamId that the client uses to connect to the SSE endpoint.
     */
    @PostMapping
    @Operation(summary = "Initiate streaming chat, returns a streamId")
    public ResponseEntity<ApiResponse<InitResponse>> initiate(
            @RequestBody ChatRequest request) {

        String tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContext.get().getTenantId();
        String userId = request.getUserId() != null ? request.getUserId() : TenantContext.get().getUserId();

        // Rate limit checks
        if (!tokenUsageService.checkTenantRateLimit(tenantId)) {
            return ResponseEntity.status(429).body(ApiResponse.of(
                    new InitResponse(null, "TENANT_RATE_LIMITED", "Tenant rate limit exceeded. Try again in a minute."),
                    TenantContext.get().getCorrelationId()));
        }
        if (userId != null && !tokenUsageService.checkUserRateLimit(tenantId, userId)) {
            tokenUsageService.recordRateLimitViolation(tenantId, userId, "user_rpm");
            return ResponseEntity.status(429).body(ApiResponse.of(
                    new InitResponse(null, "USER_RATE_LIMITED", "Slow down a bit. Please wait before sending another message."),
                    TenantContext.get().getCorrelationId()));
        }

        // Content moderation on input
        String latestUserMessage = findLatestUserMessage(request.getMessages());
        if (latestUserMessage != null) {
            ContentModerationService.ModerationResult modResult =
                    moderationService.moderateInput(latestUserMessage, tenantId, userId);
            if (!modResult.isSafe()) {
                log.warn("Moderation flagged input: tenant={}, user={}, reason={}",
                        tenantId, userId, modResult.getReason());
                return ResponseEntity.status(400).body(ApiResponse.of(
                        new InitResponse(null, "MODERATION_FAILED",
                                "Your message couldn't be processed. " + modResult.getReason()),
                        TenantContext.get().getCorrelationId()));
            }
        }

        // Create stream
        String streamId = streamingService.createStream(request.getSessionId(), tenantId);
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setStreaming(true);

        // Process in background
        processStreamAsync(streamId, request, tenantId, userId);

        return ResponseEntity.accepted().body(ApiResponse.of(
                new InitResponse(streamId, "INITIATED", "Stream created. Connect to events endpoint."),
                TenantContext.get().getCorrelationId()));
    }

    /**
     * Server-Sent Events stream for the chat.
     * Content-Type: text/event-stream
     */
    @GetMapping(value = "/{streamId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Open SSE stream for a chat session")
    public Flux<String> streamEvents(@PathVariable String streamId) {
        return streamingService.getFlux(streamId);
    }

    /**
     * Get stream metadata.
     */
    @GetMapping("/{streamId}")
    @Operation(summary = "Get stream metadata and status")
    public ResponseEntity<ApiResponse<Map<Object, Object>>> getStream(@PathVariable String streamId) {
        Map<Object, Object> meta = streamingService.getStreamMeta(streamId);
        if (meta == null || meta.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.of(meta, TenantContext.get().getCorrelationId()));
    }

    // -------------------------------------------------------------------------
    // Async stream processing
    // -------------------------------------------------------------------------

    private void processStreamAsync(String streamId, ChatRequest request, String tenantId, String userId) {
        Thread.ofVirtual().name("ai-stream-" + streamId.substring(0, 8)).start(() -> {
            try {
                // Execute chat (non-streaming under the hood, push chunks)
                AIResponse response = aiGateway.chat(request);

                // Push content as chunks (in production, the LLM would stream natively)
                String content = response.getContent();
                if (content != null) {
                    // Chunk by words to simulate streaming
                    String[] tokens = content.split("(?<=\\s)");
                    for (String token : tokens) {
                        streamingService.pushChunk(streamId, token);
                        // Throttle to simulate streaming
                        Thread.sleep(15);
                    }
                }

                // Execute any tool calls
                if (response.getToolsUsed() != null) {
                    for (AIResponse.ToolCallResult tc : response.getToolsUsed()) {
                        streamingService.pushToolCall(streamId, tc.getToolName(), tc.getArguments());

                        AIResponse.ToolCallResult result = toolExecutor.execute(
                                tc.getToolName(), tc.getArguments(), tenantId, userId, null);

                        if (result.getResult() != null) {
                            streamingService.pushChunk(streamId, "\n\n" + result.getResult() + "\n\n");
                        }
                    }
                }

                // Complete stream
                streamingService.completeStream(streamId, content, response.getTokenUsage(),
                        response.getProvider(), response.getModel());

                // Record usage
                if (response.getTokenUsage() != null) {
                    tokenUsageService.recordUsage(tenantId, userId, response.getModel(), response.getTokenUsage());
                }
            } catch (Exception e) {
                log.error("Stream processing failed: streamId={}", streamId, e);
                streamingService.errorStream(streamId, e.getMessage());
            }
        });
    }

    private String findLatestUserMessage(java.util.List<ChatRequest.Message> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatRequest.Message m = messages.get(i);
            if ("user".equals(m.getRole())) {
                return m.getContent();
            }
        }
        return null;
    }

    public record InitResponse(String streamId, String status, String message) {}
}
