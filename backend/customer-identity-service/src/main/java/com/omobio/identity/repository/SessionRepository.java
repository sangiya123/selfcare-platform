package com.omobio.identity.repository;

import com.omobio.identity.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link Session} — MySQL InnoDB durable store.
 *
 * <p>Finder methods mirror the access patterns of CustomerSessionService:
 * session lookup by user+status (listing), by token-family (rotation/replay),
 * by session+status (validation), and by tenant+session (remote revoke).
 *
 * <p>Indexes declared on the entity ensure these queries are efficient at scale.
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    /**
     * Find all sessions for a user with the given status.
     * Used by CustomerSessionService.getActiveSessions().
     *
     * @param userId the user ID
     * @param status session status (e.g. ACTIVE)
     * @return list of matching sessions
     */
    List<Session> findByUserIdAndStatus(String userId, String status);

    /**
     * Find all sessions belonging to a token family.
     * Used by CustomerSessionService.rotateRefreshToken() (replay detection) and
     * revokeTokenFamily().
     *
     * @param tokenFamilyId the token family ID
     * @return list of sessions sharing the family
     */
    List<Session> findByTokenFamilyId(String tokenFamilyId);

    /**
     * Find a session by its ID and current status.
     * Used during token refresh to confirm the session is still active.
     *
     * @param sessionId the session ID
     * @param status    expected status
     * @return the session if found and status matches
     */
    Optional<Session> findByIdAndStatus(String sessionId, String status);

    /**
     * Find a session by tenant and session ID (cross-tenant guard).
     * Used for remote session revocation to ensure callers cannot
     * touch sessions outside their tenant.
     *
     * @param tenantId  the tenant ID
     * @param sessionId the session ID
     * @return the session if found and tenant matches
     */
    Optional<Session> findByTenantIdAndSessionId(String tenantId, String sessionId);

    /**
     * Soft-expire sessions whose expiresAt has passed.
     * Run as a scheduled job or on-demand before listing.
     *
     * @param now current instant
     * @return count of updated rows
     */
    @Modifying
    @Query("UPDATE Session s SET s.status = 'EXPIRED' " +
           "WHERE s.status = 'ACTIVE' AND s.expiresAt < :now")
    int expireStaleSessions(@Param("now") Instant now);

    /**
     * Count active sessions for a user (useful for rate-limiting).
     *
     * @param userId the user ID
     * @param status expected status
     * @return number of active sessions
     */
    long countByUserIdAndStatus(String userId, String status);
}
