package com.selfcare.ai.repository;

import com.selfcare.ai.domain.PredictionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionResultRepository extends JpaRepository<PredictionResult, String> {

    Optional<PredictionResult> findFirstByTenantIdAndTargetTypeAndTargetIdAndPredictionTypeOrderByComputedAtDesc(
            String tenantId, String targetType, String targetId, String predictionType);

    List<PredictionResult> findByTenantIdAndTargetTypeAndTargetIdAndExpiresAtAfterOrderByComputedAtDesc(
            String tenantId, String targetType, String targetId, Instant asOf);

    @Query("SELECT p FROM PredictionResult p WHERE p.tenantId = :tenantId " +
           "AND p.predictionType = :type AND p.expiresAt > :asOf " +
           "AND p.level IN :levels " +
           "ORDER BY p.score DESC")
    List<PredictionResult> findActiveByTypeAndLevel(
            @Param("tenantId") String tenantId,
            @Param("type") String type,
            @Param("levels") List<String> levels,
            @Param("asOf") Instant asOf);

    @Modifying
    @Query("DELETE FROM PredictionResult p WHERE p.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
