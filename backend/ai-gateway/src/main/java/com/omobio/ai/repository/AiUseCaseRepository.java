package com.omobio.ai.repository;

import com.omobio.ai.domain.AiUseCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiUseCaseRepository extends JpaRepository<AiUseCase, String> {

    Optional<AiUseCase> findByTenantIdAndUseCaseId(String tenantId, String useCaseId);

    default Optional<AiUseCase> findEffective(String tenantId, String useCaseId) {
        Optional<AiUseCase> tenantOverride = tenantId == null
                ? Optional.empty()
                : findByTenantIdAndUseCaseId(tenantId, useCaseId);
        if (tenantOverride.isPresent()) return tenantOverride;
        return findByTenantIdAndUseCaseId(null, useCaseId);
    }

    List<AiUseCase> findByTenantId(String tenantId);

    List<AiUseCase> findByRiskTier(String riskTier);
}
