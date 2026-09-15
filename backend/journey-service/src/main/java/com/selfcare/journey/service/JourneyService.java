package com.selfcare.journey.service;

import com.selfcare.journey.domain.HistoryEntry;
import com.selfcare.journey.domain.JourneyDefinition;
import com.selfcare.journey.domain.JourneyInstance;
import com.selfcare.journey.domain.JourneyStep;
import com.selfcare.journey.repository.JourneyDefinitionRepository;
import com.selfcare.journey.repository.JourneyInstanceRepository;
import com.selfcare.platform.common.web.BadRequestException;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Journey service — orchestrates journey lifecycle operations.
 *
 * Delegates step execution to {@link JourneyEngine}.
 *
 * @see JourneyEngine
 * @see JourneyInstance
 * @see JourneyDefinition
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyService {

    private final JourneyDefinitionRepository definitionRepository;
    private final JourneyInstanceRepository instanceRepository;
    private final JourneyEngine journeyEngine;

    @Value("${selfcare.journey.instance-ttl-days:30}")
    private int instanceTtlDays;

    // -------------------------------------------------------------------------
    // Definition operations (admin)
    // -------------------------------------------------------------------------

    /**
     * List all journey definitions for the current tenant.
     *
     * @return list of journey definitions
     */
    public List<JourneyDefinition> listJourneys() {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        return definitionRepository.findByTenantId(tenantId);
    }

    /**
     * Get a specific journey definition by its business ID.
     *
     * @param journeyId the business journey ID
     * @return the journey definition
     * @throws NotFoundException if not found
     */
    public JourneyDefinition getJourney(String journeyId) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        return definitionRepository.findByTenantIdAndJourneyId(tenantId, journeyId)
                .orElseThrow(() -> new NotFoundException("Journey", journeyId));
    }

    /**
     * Create a new journey definition in DRAFT status.
     *
     * @param definition the journey definition (id null, status DRAFT)
     * @return the saved journey definition with generated ID
     * @throws BadRequestException if journeyId is already in use
     */
    @Transactional
    public JourneyDefinition createJourney(JourneyDefinition definition) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        if (definitionRepository.existsByTenantIdAndJourneyId(tenantId, definition.getJourneyId())) {
            throw new BadRequestException("Journey ID already in use: " + definition.getJourneyId());
        }

        definition.setId(null);
        definition.setTenantId(tenantId);
        definition.setStatus(JourneyDefinition.JourneyStatus.DRAFT);
        definition.setVersion(1);

        log.info("Creating journey: tenant={}, journeyId={}", tenantId, definition.getJourneyId());
        return definitionRepository.save(definition);
    }

    /**
     * Update a journey definition (only allowed in DRAFT status).
     *
     * @param journeyId the business journey ID
     * @param updated   the updated definition
     * @return the saved journey definition
     * @throws NotFoundException     if not found
     * @throws BadRequestException    if journey is not in DRAFT status
     */
    @Transactional
    public JourneyDefinition updateJourney(String journeyId, JourneyDefinition updated) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyDefinition existing = definitionRepository.findByTenantIdAndJourneyId(tenantId, journeyId)
                .orElseThrow(() -> new NotFoundException("Journey", journeyId));

        if (existing.getStatus() != JourneyDefinition.JourneyStatus.DRAFT) {
            throw new BadRequestException("Can only update journeys in DRAFT status");
        }

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setSteps(updated.getSteps());
        existing.setEntryStepId(updated.getEntryStepId());
        existing.setTags(updated.getTags());
        existing.setUpdatedAt(Instant.now());

        log.info("Updated journey: tenant={}, journeyId={}", tenantId, journeyId);
        return definitionRepository.save(existing);
    }

    /**
     * Publish a DRAFT journey — makes it immutable and runnable.
     *
     * @param journeyId the business journey ID
     * @return the published journey definition
     */
    @Transactional
    public JourneyDefinition publishJourney(String journeyId) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyDefinition definition = definitionRepository.findByTenantIdAndJourneyId(tenantId, journeyId)
                .orElseThrow(() -> new NotFoundException("Journey", journeyId));

        if (definition.getStatus() != JourneyDefinition.JourneyStatus.DRAFT) {
            throw new BadRequestException("Can only publish DRAFT journeys");
        }

        validateSteps(definition.getSteps());
        validateEntryStepId(definition);

        definition.setStatus(JourneyDefinition.JourneyStatus.PUBLISHED);
        definition.setPublishedAt(Instant.now());

        log.info("Published journey: tenant={}, journeyId={}", tenantId, journeyId);
        return definitionRepository.save(definition);
    }

    /**
     * Archive a PUBLISHED journey — makes it non-runnable.
     *
     * @param journeyId the business journey ID
     * @return the archived journey definition
     */
    @Transactional
    public JourneyDefinition archiveJourney(String journeyId) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyDefinition definition = definitionRepository.findByTenantIdAndJourneyId(tenantId, journeyId)
                .orElseThrow(() -> new NotFoundException("Journey", journeyId));

        definition.setStatus(JourneyDefinition.JourneyStatus.ARCHIVED);
        log.info("Archived journey: tenant={}, journeyId={}", tenantId, journeyId);
        return definitionRepository.save(definition);
    }

    // -------------------------------------------------------------------------
    // Instance operations (user-facing)
    // -------------------------------------------------------------------------

    /**
     * Start a new journey instance for the current user.
     *
     * @param journeyId the business journey ID to start
     * @param userId    the user starting the journey
     * @param ipAddress client IP address
     * @param userAgent client user agent
     * @return the created journey instance
     */
    @Transactional
    public JourneyInstance startJourney(String journeyId, String userId,
                                        String ipAddress, String userAgent) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyDefinition definition = definitionRepository.findByTenantIdAndJourneyId(tenantId, journeyId)
                .orElseThrow(() -> new NotFoundException("Journey", journeyId));

        if (definition.getStatus() != JourneyDefinition.JourneyStatus.PUBLISHED) {
            throw new BadRequestException("Journey is not available: " + journeyId);
        }

        // Resume existing active instance if one exists
        var existing = instanceRepository.findByTenantIdAndUserIdAndJourneyIdAndStatus(
                tenantId, userId, journeyId, JourneyInstance.InstanceStatus.ACTIVE);
        if (existing.isPresent()) {
            log.info("Resuming existing journey instance: tenant={}, journeyId={}, userId={}",
                    tenantId, journeyId, userId);
            return existing.get();
        }

        JourneyInstance instance = JourneyInstance.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .journeyId(journeyId)
                .journeyVersion(definition.getVersion())
                .currentStepId(definition.getEntryStepId())
                .state(new HashMap<>())
                .status(JourneyInstance.InstanceStatus.ACTIVE)
                .history(new java.util.ArrayList<>())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        JourneyStep entryStep = findStep(definition, definition.getEntryStepId());
        JourneyInstance savedInstance = instanceRepository.save(instance);

        recordHistoryEntry(savedInstance, entryStep, Instant.now(), null, "STARTED", null);

        log.info("Started journey: tenant={}, journeyId={}, userId={}, instanceId={}",
                tenantId, journeyId, userId, savedInstance.getId());
        return savedInstance;
    }

    /**
     * Get the current step information for a journey instance.
     *
     * @param instanceId the instance ID
     * @return the current step the user is on, with step config
     */
    public StepResult getCurrentStep(String instanceId) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyInstance instance = instanceRepository.findById(instanceId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("JourneyInstance", instanceId));

        JourneyDefinition definition = definitionRepository.findByTenantIdAndJourneyId(
                tenantId, instance.getJourneyId())
                .orElseThrow(() -> new NotFoundException("Journey", instance.getJourneyId()));

        JourneyStep step = findStep(definition, instance.getCurrentStepId());

        return StepResult.builder()
                .instanceId(instanceId)
                .stepId(step.getStepId())
                .stepType(step.getType().name())
                .title(step.getTitle())
                .description(step.getDescription())
                .config(step.getConfig())
                .nextStepIds(step.getNextStepIds())
                .currentState(instance.getState())
                .status(instance.getStatus().name())
                .build();
    }

    /**
     * Advance a journey instance from the current step.
     *
     * @param instanceId the instance ID
     * @param inputData  data submitted by the user at the current step
     * @return the updated instance with new current step
     */
    @Transactional
    public JourneyInstance advanceStep(String instanceId, Map<String, Object> inputData) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyInstance instance = instanceRepository.findById(instanceId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("JourneyInstance", instanceId));

        if (instance.getStatus() != JourneyInstance.InstanceStatus.ACTIVE) {
            throw new BadRequestException("Journey instance is not active: " + instance.getStatus());
        }

        JourneyDefinition definition = definitionRepository.findByTenantIdAndJourneyId(
                tenantId, instance.getJourneyId())
                .orElseThrow(() -> new NotFoundException("Journey", instance.getJourneyId()));

        JourneyStep currentStep = findStep(definition, instance.getCurrentStepId());

        // Merge input data into state
        Map<String, Object> mergedState = new HashMap<>(instance.getState());
        if (inputData != null) {
            mergedState.putAll(inputData);
        }

        // Execute the step via the engine
        Map<String, Object> outputData = journeyEngine.executeStep(
                currentStep, mergedState, instance);

        // Merge output into state
        mergedState.putAll(outputData != null ? outputData : Map.of());

        // Determine next step
        String nextStepId = journeyEngine.determineNextStep(
                currentStep, mergedState, instance);

        Instant now = Instant.now();
        HistoryEntry entry = HistoryEntry.builder()
                .stepId(currentStep.getStepId())
                .stepType(currentStep.getType().name())
                .enteredAt(instance.getUpdatedAt() != null ? instance.getUpdatedAt() : now)
                .exitedAt(now)
                .outcome("COMPLETED")
                .inputData(inputData)
                .outputData(outputData)
                .durationMs(java.time.Duration.between(
                        instance.getUpdatedAt() != null ? instance.getUpdatedAt() : now, now).toMillis())
                .nextStepId(nextStepId)
                .build();

        instance.addHistoryEntry(entry);
        instance.setState(mergedState);

        if (nextStepId == null) {
            // Journey complete
            instance.setStatus(JourneyInstance.InstanceStatus.COMPLETED);
            instance.setCompletedAt(now);
            instance.setCurrentStepId(null);
        } else {
            JourneyStep nextStep = findStep(definition, nextStepId);
            if (nextStep.getType() == JourneyStep.StepType.WAIT) {
                instance.setStatus(JourneyInstance.InstanceStatus.WAITING);
            }
            instance.setCurrentStepId(nextStepId);
        }

        log.info("Advanced journey step: instanceId={}, from={}, to={}, status={}",
                instanceId, currentStep.getStepId(), nextStepId, instance.getStatus());
        return instanceRepository.save(instance);
    }

    /**
     * Abandon a journey instance (user-initiated stop).
     *
     * @param instanceId the instance ID
     * @param reason     optional reason
     */
    @Transactional
    public void abandonJourney(String instanceId, String reason) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();

        JourneyInstance instance = instanceRepository.findById(instanceId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new NotFoundException("JourneyInstance", instanceId));

        if (instance.getStatus() == JourneyInstance.InstanceStatus.ACTIVE
                || instance.getStatus() == JourneyInstance.InstanceStatus.WAITING) {
            instance.setStatus(JourneyInstance.InstanceStatus.ABANDONED);
            instance.setCompletedAt(Instant.now());
            instance.setFailureReason(reason);
            instanceRepository.save(instance);
            log.info("Abandoned journey: instanceId={}, reason={}", instanceId, reason);
        }
    }

    /**
     * List all instances for the current user.
     *
     * @param journeyId optional filter by journey ID
     * @param status    optional filter by status
     * @return list of journey instances
     */
    public List<JourneyInstance> listInstances(String journeyId, JourneyInstance.InstanceStatus status) {
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        String userId = com.selfcare.platform.common.tenant.TenantContext.get().getUserId();

        if (journeyId != null && status != null) {
            return instanceRepository.findByTenantIdAndJourneyIdAndStatus(tenantId, journeyId, status);
        } else if (journeyId != null) {
            return instanceRepository.findByTenantIdAndJourneyId(tenantId, journeyId);
        } else if (status != null) {
            return instanceRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, status);
        } else {
            return instanceRepository.findByTenantIdAndUserId(tenantId, userId);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateSteps(List<JourneyStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new BadRequestException("Journey must have at least one step");
        }
    }

    private void validateEntryStepId(JourneyDefinition definition) {
        if (definition.getEntryStepId() == null
                || definition.getSteps().stream()
                .noneMatch(s -> s.getStepId().equals(definition.getEntryStepId()))) {
            throw new BadRequestException("Invalid entryStepId: " + definition.getEntryStepId());
        }
    }

    private JourneyStep findStep(JourneyDefinition definition, String stepId) {
        return definition.getSteps().stream()
                .filter(s -> s.getStepId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Step not found: " + stepId));
    }

    private void recordHistoryEntry(JourneyInstance instance, JourneyStep step,
                                    Instant enteredAt, Map<String, Object> inputData,
                                    String outcome, Map<String, Object> outputData) {
        HistoryEntry entry = HistoryEntry.builder()
                .stepId(step.getStepId())
                .stepType(step.getType().name())
                .enteredAt(enteredAt)
                .outcome(outcome)
                .inputData(inputData)
                .outputData(outputData)
                .build();
        instance.addHistoryEntry(entry);
    }

    // -------------------------------------------------------------------------
    // DTO
    // -------------------------------------------------------------------------

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StepResult {
        private String instanceId;
        private String stepId;
        private String stepType;
        private String title;
        private String description;
        private Map<String, Object> config;
        private List<String> nextStepIds;
        private Map<String, Object> currentState;
        private String status;
    }
}
