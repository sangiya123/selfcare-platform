package com.selfcare.ai.service;

import com.selfcare.ai.domain.ChatSession;
import com.selfcare.ai.domain.ChatSession.ChatMessage;
import com.selfcare.ai.repository.ChatSessionRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Chat session service — manages persistent conversation history.
 *
 * Sessions are stored in MongoDB and expire automatically after the configured
 * retention period (default 7 days). Within a session, messages are accumulated
 * and used as conversation context for the AI.
 *
 * Session lifecycle:
 *   - getOrCreate(): if sessionId is null, create a new session; otherwise, fetch
 *   - addMessage(): append a message to a session
 *   - getHistory(): fetch full message history
 *   - delete(): remove a session and its messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int DEFAULT_SESSION_RETENTION_DAYS = 7;
    private static final int DEFAULT_MAX_MESSAGES = 100;

    private final ChatSessionRepository sessionRepository;

    /**
     * List all sessions for the current user.
     */
    public List<ChatSession> listSessions() {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        if (userId == null) {
            return List.of();
        }
        return sessionRepository.findByTenantIdAndUserIdAndActiveTrueOrderByLastActivityAtDesc(tenantId, userId);
    }

    /**
     * Get an existing session or create a new one for the current user.
     */
    public ChatSession getOrCreateSession(String sessionId) {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        if (sessionId != null && !sessionId.isBlank()) {
            return sessionRepository.findById(sessionId)
                    .filter(s -> s.getTenantId().equals(tenantId))
                    .filter(s -> userId == null || s.getUserId().equals(userId))
                    .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        }

        // Create a new session
        ChatSession newSession = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .title("New Conversation")
                .active(true)
                .messages(new ArrayList<>())
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .expiresAt(Instant.now().plus(DEFAULT_SESSION_RETENTION_DAYS, ChronoUnit.DAYS))
                .build();

        ChatSession saved = sessionRepository.save(newSession);
        log.info("Created new chat session: id={}, user={}", saved.getId(), userId);
        return saved;
    }

    /**
     * Get full history of a session.
     */
    public ChatSession getHistory(String sessionId) {
        String tenantId = TenantContext.get().getTenantId();
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    /**
     * Add a message to a session and update its last activity timestamp.
     */
    public ChatSession addMessage(String sessionId, ChatMessage message) {
        ChatSession session = getHistory(sessionId);

        if (message.getTimestamp() == null) {
            message.setTimestamp(Instant.now());
        }
        if (session.getMessages() == null) {
            session.setMessages(new ArrayList<>());
        }

        // Enforce max message limit (rolling window)
        if (session.getMessages().size() >= DEFAULT_MAX_MESSAGES) {
            // Remove oldest non-system messages to make room
            session.getMessages().removeIf(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()));
            while (session.getMessages().size() >= DEFAULT_MAX_MESSAGES) {
                session.getMessages().remove(0);
            }
        }

        session.getMessages().add(message);
        session.setLastActivityAt(Instant.now());

        // Update title based on first user message if still default
        if ("New Conversation".equals(session.getTitle()) && "user".equals(message.getRole())) {
            String firstMsg = message.getContent();
            if (firstMsg != null) {
                session.setTitle(firstMsg.length() > 50
                        ? firstMsg.substring(0, 47) + "..."
                        : firstMsg);
            }
        }

        ChatSession saved = sessionRepository.save(session);
        log.debug("Added message to session: sessionId={}, role={}", sessionId, message.getRole());
        return saved;
    }

    /**
     * Soft-delete a session.
     */
    public void delete(String sessionId) {
        ChatSession session = getHistory(sessionId);
        session.setActive(false);
        sessionRepository.save(session);
        log.info("Soft-deleted chat session: {}", sessionId);
    }
}
