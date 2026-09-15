package com.selfcare.identity.repository;

import com.selfcare.identity.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    /**
     * Find the active (non-superseded) consent for a (user, purpose) tuple.
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.tenantId = :tenantId AND c.userId = :userId " +
           "AND c.purpose = :purpose AND c.supersededAt IS NULL ORDER BY c.capturedAt DESC")
    Optional<ConsentRecord> findActive(@Param("tenantId") String tenantId,
                                       @Param("userId") String userId,
                                       @Param("purpose") String purpose);

    /**
     * All consents (including superseded) for a user, ordered by most recent.
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.tenantId = :tenantId AND c.userId = :userId " +
           "ORDER BY c.capturedAt DESC")
    List<ConsentRecord> findAllByUser(@Param("tenantId") String tenantId,
                                      @Param("userId") String userId);

    /**
     * All active consents for a user, across all purposes.
     */
    @Query("SELECT c FROM ConsentRecord c WHERE c.tenantId = :tenantId AND c.userId = :userId " +
           "AND c.supersededAt IS NULL ORDER BY c.capturedAt DESC")
    List<ConsentRecord> findAllActiveByUser(@Param("tenantId") String tenantId,
                                            @Param("userId") String userId);

    /**
     * All users with a given consent (e.g. all users who granted marketing).
     */
    @Query("SELECT DISTINCT c.userId FROM ConsentRecord c WHERE c.tenantId = :tenantId " +
           "AND c.purpose = :purpose AND c.granted = true AND c.supersededAt IS NULL")
    List<String> findUsersWithConsent(@Param("tenantId") String tenantId,
                                      @Param("purpose") String purpose);
}
