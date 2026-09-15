package com.selfcare.approval.repository;

import com.selfcare.approval.domain.ApprovalRequest;
import com.selfcare.approval.domain.ApprovalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /**
     * Lookup by the externally visible UUID request_id.
     */
    Optional<ApprovalRequest> findByRequestId(String requestId);

    /**
     * All PENDING requests for a tenant — used to compute queue size
     * and to display the reviewer's inbox.
     */
    List<ApprovalRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(
            String tenantId, ApprovalStatus status);

    /**
     * Recent history across all statuses for a tenant.
     */
    List<ApprovalRequest> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * PENDING requests whose expiry has passed. The scheduled job picks
     * these up and marks them EXPIRED.
     */
    @Query("SELECT a FROM ApprovalRequest a " +
           "WHERE a.status = com.selfcare.approval.domain.ApprovalStatus.PENDING " +
           "AND a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    List<ApprovalRequest> findStalePending(@Param("now") Instant now);

    /**
     * All PENDING requests in the system, across tenants. Used by the
     * scheduled expiry sweep when running in single-tenant deployments.
     */
    @Query("SELECT a FROM ApprovalRequest a " +
           "WHERE a.status = com.selfcare.approval.domain.ApprovalStatus.PENDING " +
           "AND a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    List<ApprovalRequest> findStalePendingAllTenants(@Param("now") Instant now);

    /**
     * Filter by tenant + status + action — supports the list endpoint.
     */
    @Query("SELECT a FROM ApprovalRequest a " +
           "WHERE (:tenantId IS NULL OR a.tenantId = :tenantId) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "ORDER BY a.createdAt DESC")
    List<ApprovalRequest> search(@Param("tenantId") String tenantId,
                                 @Param("status") ApprovalStatus status,
                                 @Param("action") String action,
                                 Pageable pageable);

    /**
     * All PENDING requests for a tenant. Approvers see this as their inbox.
     */
    List<ApprovalRequest> findByTenantIdAndStatus(String tenantId, ApprovalStatus status);

    long countByTenantIdAndStatus(String tenantId, ApprovalStatus status);
}
