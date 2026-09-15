package com.selfcare.journey.repository;

import com.selfcare.journey.domain.JourneyDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link JourneyDefinition}.
 *
 * Provides access to versioned, per-tenant journey templates.
 */
@Repository
public interface JourneyDefinitionRepository extends MongoRepository<JourneyDefinition, String> {

    /**
     * Find all journey definitions for a tenant.
     */
    List<JourneyDefinition> findByTenantId(String tenantId);

    /**
     * Find a journey definition by its business ID within a tenant.
     */
    Optional<JourneyDefinition> findByTenantIdAndJourneyId(String tenantId, String journeyId);

    /**
     * Find all definitions for a tenant by status.
     */
    List<JourneyDefinition> findByTenantIdAndStatus(String tenantId, JourneyDefinition.JourneyStatus status);

    /**
     * Check whether a journey ID is already used for a tenant.
     */
    boolean existsByTenantIdAndJourneyId(String tenantId, String journeyId);
}
