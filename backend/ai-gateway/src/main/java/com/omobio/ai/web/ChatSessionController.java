package com.omobio.ai.web;

import com.omobio.ai.domain.ChatSession;
import com.omobio.ai.service.ChatSessionService;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import com.omobio.platform.common.web.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chat session REST API.
 *
 * Endpoints:
 *   GET    /api/v1/ai/sessions                — list current user's sessions
 *   POST   /api/v1/ai/sessions                — create or get current session
 *   GET    /api/v1/ai/sessions/{id}/history   — full message history
 *   POST   /api/v1/ai/sessions/{id}/messages  — add a message
 *   DELETE /api/v1/ai/sessions/{id}           — delete session
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/sessions")
@RequiredArgsConstructor
@Tag(name = "Chat Sessions", description = "Conversation session history")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * List all chat sessions for the current user.
     */
    @GetMapping
    @Operation(summary = "List chat sessions for the current user")
    public ResponseEntity<ApiResponse<List<ChatSession>>> listSessions() {
        List<ChatSession> sessions = chatSessionService.listSessions();
        return ResponseEntity.ok(ApiResponse.of(sessions, TenantContext.get().getCorrelationId()));
    }

    /**
     * Get or create the current active session.
     * If sessionId is provided in the body, returns that session (must belong to user).
     */
    @PostMapping
    @Operation(summary = "Get or create the current chat session")
    public ResponseEntity<ApiResponse<ChatSession>> getOrCreate(@RequestBody(required = false) GetOrCreateRequest request) {
        String sessionId = request != null ? request.sessionId() : null;
        ChatSession session = chatSessionService.getOrCreateSession(sessionId);
        return ResponseEntity.ok(ApiResponse.of(session, TenantContext.get().getCorrelationId()));
    }

    /**
     * Get the full message history of a session.
     */
    @GetMapping("/{id}/history")
    @Operation(summary = "Get full message history of a session")
    public ResponseEntity<ApiResponse<ChatSession>> getHistory(@PathVariable String id) {
        ChatSession session = chatSessionService.getHistory(id);
        return ResponseEntity.ok(ApiResponse.of(session, TenantContext.get().getCorrelationId()));
    }

    /**
     * Add a message to a session.
     */
    @PostMapping("/{id}/messages")
    @Operation(summary = "Add a message to a session")
    public ResponseEntity<ApiResponse<ChatSession>> addMessage(
            @PathVariable String id,
            @Valid @RequestBody AddMessageRequest request) {

        ChatSession.ChatMessage message = ChatSession.ChatMessage.builder()
                .role(request.role())
                .content(request.content())
                .toolCalls(request.toolCalls())
                .build();

        ChatSession updated = chatSessionService.addMessage(id, message);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(updated, TenantContext.get().getCorrelationId()));
    }

    /**
     * Delete a session.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a session")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        chatSessionService.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
    }

    // ------------------------------------------------------------------
    // Request records
    // ------------------------------------------------------------------

    public record GetOrCreateRequest(String sessionId) {}

    public record AddMessageRequest(
            @NotBlank String role,
            @NotBlank String content,
            List<ChatSession.ToolCall> toolCalls
    ) {}
}
