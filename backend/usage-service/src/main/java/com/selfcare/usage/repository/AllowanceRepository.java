package com.selfcare.usage.repository;

import com.selfcare.usage.domain.Allowance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AllowanceRepository extends JpaRepository<Allowance, String> {

    List<Allowance> findByTenantIdAndConnectionIdAndStatus(
            String tenantId, String connectionId, String status);

    @Query("SELECT a FROM Allowance a WHERE a.tenantId = :tenantId " +
           "AND a.connectionId = :connectionId AND a.status = 'ACTIVE' " +
           "ORDER BY a.expiresAt ASC")
    List<Allowance> findActiveAllowances(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId);

    Optional<Allowance> findByTenantIdAndConnectionIdAndAllowanceId(
            String tenantId, String connectionId, String allowanceId);

    @Modifying
    @Query("UPDATE Allowance a SET a.status = 'EXPIRED' WHERE a.status = 'ACTIVE' " +
           "AND a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    int markExpiredAsOf(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE Allowance a SET a.usedUnits = :used, a.remainingUnits = :remaining " +
           "WHERE a.tenantId = :tenantId AND a.connectionId = :connectionId AND a.allowanceId = :id")
    int updateConsumption(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("id") String id,
            @Param("used") long used,
            @Param("remaining") long remaining);
}
