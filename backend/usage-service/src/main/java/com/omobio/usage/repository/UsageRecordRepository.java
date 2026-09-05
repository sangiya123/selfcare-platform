package com.omobio.usage.repository;

import com.omobio.usage.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    Optional<UsageRecord> findFirstByTenantIdAndConnectionIdOrderByRecordedAtDesc(
            String tenantId, String connectionId);

    @Query("SELECT r FROM UsageRecord r WHERE r.tenantId = :tenantId " +
           "AND r.connectionId = :connectionId AND r.recordedAt >= :since " +
           "ORDER BY r.recordedAt DESC")
    List<UsageRecord> findRecentForConnection(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("since") Instant since);

    @Modifying
    @Query("UPDATE UsageRecord r SET r.isStale = true WHERE r.tenantId = :tenantId " +
           "AND r.connectionId = :connectionId AND r.recordedAt < :cutoff")
    int markStaleBefore(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM UsageRecord r WHERE r.recordedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
