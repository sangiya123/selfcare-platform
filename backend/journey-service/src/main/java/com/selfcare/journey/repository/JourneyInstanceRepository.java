package com.selfcare.journey.repository;

import com.selfcare.journey.domain.JourneyInstance;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link JourneyInstance}.
 *
 * Tracks active and historical journey executions per user.
 */
@Repository
public interface JourneyInstanceRepository extends MongoRepository<JourneyInstance, String> {

    /**
     * Find all instances for a user within a tenant.
     */
    List<JourneyInstance> findByTenantIdAndUserId(String tenantId, String userId);

    /**
     * Find all active instances for a user within a tenant.
     */
    List<JourneyInstance> findByTenantIdAndUserIdAndStatus(
            String tenantId, String userId, JourneyInstance.InstanceStatus status);

    /**
     * Find a specific active instance for a user and journey.
     */
    Optional<JourneyInstance> findByTenantIdAndUserIdAndJourneyIdAndStatus(
            String tenantId, String userId, String journeyId, JourneyInstance.InstanceStatus status);

    /**
     * Find all instances for a specific journey definition.
     */
    List<JourneyInstance> findByTenantIdAndJourneyId(String tenantId, String journeyId);

    /**
     * Find all instances for a journey with a given status.
     */
    List<JourneyInstance> findByTenantIdAndJourneyIdAndStatus(
            String tenantId, String journeyId, JourneyInstance.InstanceStatus status);

    /**
     * Count active instances per journey (for analytics).
     */
    long countByTenantIdAndJourneyIdAndStatus(
            String tenantId, String journeyId, JourneyInstance.InstanceStatus status);
}
