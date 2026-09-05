package com.omobio.reporting.repository;

import com.omobio.reporting.domain.ReportExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ReportExecution}.
 */
@Repository
public interface ReportExecutionRepository extends JpaRepository<ReportExecution, String> {

    /**
     * Find all executions for a report, paginated.
     */
    Page<ReportExecution> findByTenantIdAndReportIdOrderByCreatedAtDesc(
            String tenantId, String reportId, Pageable pageable);

    /**
     * Find all executions for a tenant, paginated.
     */
    Page<ReportExecution> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Find the most recent execution for a report.
     */
    Optional<ReportExecution> findFirstByTenantIdAndReportIdOrderByCreatedAtDesc(
            String tenantId, String reportId);

    /**
     * Find executions by status.
     */
    List<ReportExecution> findByStatus(String status);

    /**
     * Count executions per report.
     */
    long countByTenantIdAndReportId(String tenantId, String reportId);

    /**
     * Count failed executions in a time range.
     */
    @Query("SELECT COUNT(e) FROM ReportExecution e WHERE e.tenantId = ?1 AND e.status = 'FAILED' " +
           "AND e.createdAt >= ?2 AND e.createdAt <= ?3")
    long countFailedInRange(String tenantId, Instant from, Instant to);
}
