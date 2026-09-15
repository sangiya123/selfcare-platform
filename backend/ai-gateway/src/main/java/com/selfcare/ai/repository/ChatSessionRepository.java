package com.selfcare.ai.repository;

import com.selfcare.ai.domain.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link ChatSession}.
 *
 * Finder methods mirror the access patterns of ChatSessionService:
 * - lookup by session ID
 * - list by user/tenant (paginated)
 * - cleanup of expired sessions
 */
@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    /**
     * Find all sessions for a user, sorted by last activity.
     */
    List<ChatSession> findByUserIdOrderByLastActivityAtDesc(String userId);

    /**
     * Find all sessions for a user, paginated.
     */
    Page<ChatSession> findByUserId(String userId, Pageable pageable);

    /**
     * Find all sessions for a tenant.
     */
    List<ChatSession> findByTenantId(String tenantId);

    /**
     * Find a session by tenant and user.
     */
    Optional<ChatSession> findByIdAndUserId(String id, String userId);

    /**
     * Find all active sessions for a tenant + user, sorted by last activity.
     */
    List<ChatSession> findByTenantIdAndUserIdAndActiveTrueOrderByLastActivityAtDesc(String tenantId, String userId);

    /**
     * Find the most recent active session for a user.
     */
    Optional<ChatSession> findFirstByUserIdAndActiveTrueOrderByLastActivityAtDesc(String userId);

    /**
     * Find all expired sessions (for cleanup).
     */
    @Query("{ 'expiresAt': { $lt: ?0 }, 'active': true }")
    List<ChatSession> findExpiredSessions(Instant now);

    /**
     * Count active sessions for a user.
     */
    long countByUserIdAndActiveTrue(String userId);
}
