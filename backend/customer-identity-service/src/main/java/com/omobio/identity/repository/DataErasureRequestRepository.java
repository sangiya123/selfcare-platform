package com.omobio.identity.repository;

import com.omobio.identity.domain.DataErasureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataErasureRequestRepository extends JpaRepository<DataErasureRequest, UUID> {

    /**
     * Find an active erasure request for a given (tenantId, userIdHash).
     * "Active" means not in FAILED state — pending, in-progress, or completed.
     */
    @Query("SELECT d FROM DataErasureRequest d WHERE d.tenantId = :tenantId " +
           "AND d.userIdHash = :userIdHash AND d.status != 'FAILED' " +
           "ORDER BY d.requestedAt DESC")
    Optional<DataErasureRequest> findActiveForUser(@Param("tenantId") String tenantId,
                                                   @Param("userIdHash") String userIdHash);

    @Query("SELECT d FROM DataErasureRequest d WHERE d.status = 'PENDING' " +
           "ORDER BY d.requestedAt ASC")
    List<DataErasureRequest> findPending();

    @Query("SELECT d FROM DataErasureRequest d WHERE d.status = 'IN_PROGRESS' " +
           "AND d.startedAt < :cutoff")
    List<DataErasureRequest> findStuckInProgress(@Param("cutoff") java.time.Instant cutoff);
}
