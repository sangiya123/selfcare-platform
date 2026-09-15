package com.selfcare.ai.repository;

import com.selfcare.ai.domain.AiEvaluationSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link AiEvaluationSet} — versioned evaluation test-case bundles.
 */
@Repository
public interface AiEvaluationSetRepository extends JpaRepository<AiEvaluationSet, String> {

    /**
     * Get the active set for a use case + type (BASELINE, RED_TEAM, etc.).
     */
    @Query("SELECT s FROM AiEvaluationSet s WHERE s.useCaseId = :useCaseId " +
           "AND s.setType = :setType AND s.isActive = true " +
           "ORDER BY s.version DESC")
    List<AiEvaluationSet> findActiveByUseCaseAndType(
            @Param("useCaseId") String useCaseId,
            @Param("setType") String setType);

    /**
     * Get the latest version of a set for a use case + type.
     */
    Optional<AiEvaluationSet> findFirstByUseCaseIdAndSetTypeOrderByVersionDesc(
            String useCaseId, String setType);

    /**
     * All sets for a use case, latest version first.
     */
    List<AiEvaluationSet> findByUseCaseIdOrderByVersionDesc(String useCaseId);
}
