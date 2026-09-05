package com.omobio.ai.repository;

import com.omobio.ai.domain.AiKillSwitch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiKillSwitchRepository extends JpaRepository<AiKillSwitch, String> {

    @Query("SELECT k FROM AiKillSwitch k WHERE k.active = true " +
           "AND (k.expiresAt IS NULL OR k.expiresAt > :now) " +
           "AND (k.tenantId = :tenantId OR k.tenantId IS NULL) " +
           "AND (k.useCaseId = :useCaseId OR k.useCaseId IS NULL)")
    List<AiKillSwitch> findActive(
            @Param("tenantId") String tenantId,
            @Param("useCaseId") String useCaseId,
            @Param("now") Instant now);

    default List<AiKillSwitch> findActiveForUseCase(String tenantId, String useCaseId) {
        return findActive(tenantId, useCaseId, Instant.now());
    }

    default List<AiKillSwitch> findActiveForProvider(String provider) {
        return findAll().stream()
                .filter(k -> k.getActive() && "PROVIDER".equals(k.getScope())
                        && provider.equals(k.getProvider())
                        && (k.getExpiresAt() == null || k.getExpiresAt().isAfter(Instant.now())))
                .toList();
    }

    Optional<AiKillSwitch> findByUseCaseIdAndScopeAndActiveTrue(String useCaseId, String scope);
}
