package com.selfcare.ai.repository;

import com.selfcare.ai.domain.AiEvaluationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiEvaluationResultRepository extends JpaRepository<AiEvaluationResult, String> {

    Optional<AiEvaluationResult> findFirstByUseCaseIdOrderByRunAtDesc(String useCaseId);

    @Query("SELECT e FROM AiEvaluationResult e WHERE e.useCaseId = :useCaseId ORDER BY e.runAt DESC")
    List<AiEvaluationResult> findRecentByUseCase(@Param("useCaseId") String useCaseId, Pageable pageable);

    @Query("SELECT e FROM AiEvaluationResult e WHERE e.useCaseId = :useCaseId " +
           "AND e.model = :model ORDER BY e.runAt DESC")
    List<AiEvaluationResult> findByUseCaseAndModel(
            @Param("useCaseId") String useCaseId,
            @Param("model") String model,
            Pageable pageable);

    Page<AiEvaluationResult> findByTenantIdOrderByRunAtDesc(String tenantId, Pageable pageable);

    /**
     * Evaluation results for a use case that ran before the given cutoff.
     * Used by the AI retention batch job to enforce per-use-case retention_days.
     */
    List<AiEvaluationResult> findByUseCaseIdAndRunAtBefore(String useCaseId, java.time.Instant cutoff);
}
