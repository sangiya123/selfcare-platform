package com.selfcare.reporting.repository;

import com.selfcare.reporting.domain.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ReportDefinition}.
 */
@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, String> {

    /**
     * List all report definitions for a tenant.
     */
    List<ReportDefinition> findByTenantId(String tenantId);

    /**
     * Find a report definition by tenant and name.
     */
    Optional<ReportDefinition> findByTenantIdAndName(String tenantId, String name);

    /**
     * Find all active definitions with a schedule (for the scheduler).
     */
    @Query("SELECT r FROM ReportDefinition r WHERE r.status = 'ACTIVE' AND r.schedule IS NOT NULL AND r.schedule <> ''")
    List<ReportDefinition> findAllScheduledActive();

    /**
     * Check uniqueness of (tenantId, name).
     */
    boolean existsByTenantIdAndName(String tenantId, String name);
}
