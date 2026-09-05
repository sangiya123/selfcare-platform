package com.omobio.audit.repository;

import com.omobio.audit.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA repository for {@link AuditEvent}.
 *
 * The audit table is append-only — only INSERT and SELECT operations are supported
 * at the application level. No UPDATE, no DELETE.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    /**
     * Get a single event by ID.
     */
    Optional<AuditEvent> findById(String id);

    /**
     * List all events for a tenant, paginated, ordered by occurred_at desc.
     */
    Page<AuditEvent> findByTenantIdOrderByOccurredAtDesc(String tenantId, Pageable pageable);

    /**
     * List events for a tenant by user ID, paginated.
     */
    Page<AuditEvent> findByTenantIdAndUserIdOrderByOccurredAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * List events for a tenant by action, paginated.
     */
    Page<AuditEvent> findByTenantIdAndActionOrderByOccurredAtDesc(
            String tenantId, String action, Pageable pageable);

    /**
     * List events for a tenant by resource type, paginated.
     */
    Page<AuditEvent> findByTenantIdAndResourceTypeOrderByOccurredAtDesc(
            String tenantId, String resourceType, Pageable pageable);

    /**
     * List events for a tenant within a date range, paginated.
     */
    Page<AuditEvent> findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            String tenantId, Instant from, Instant to, Pageable pageable);

    /**
     * Multi-criteria query for audit events.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId " +
           "AND (:userId IS NULL OR a.userId = :userId) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:resourceType IS NULL OR a.resourceType = :resourceType) " +
           "AND (:from IS NULL OR a.occurredAt >= :from) " +
           "AND (:to IS NULL OR a.occurredAt <= :to) " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditEvent> search(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Admin search across all tenants.
     */
    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:resourceType IS NULL OR a.resourceType = :resourceType) " +
           "AND (:from IS NULL OR a.occurredAt >= :from) " +
           "AND (:to IS NULL OR a.occurredAt <= :to) " +
           "ORDER BY a.occurredAt DESC")
    Page<AuditEvent> adminSearch(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Count events by action.
     */
    long countByTenantIdAndAction(String tenantId, String action);

    /**
     * Count events by user.
     */
    long countByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Delete events recorded before the given cutoff.
     * Used by {@link com.omobio.audit.scheduler.AuditRetentionJob} to enforce
     * the platform retention policy.
     */
    long deleteByRecordedAtBefore(Instant cutoff);

    /**
     * Count events recorded before the given cutoff (used for log-only retention check).
     */
    long countByRecordedAtBefore(Instant cutoff);
}
