package com.selfcare.journey.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * An active instance of a journey for a specific user.
 *
 * Tracks the current position in the journey, collected state,
 * and full history of step transitions.
 *
 * Stored in MongoDB for flexible schema and fast writes.
 *
 * @see JourneyDefinition
 * @see HistoryEntry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "journey_instances")
@CompoundIndex(name = "ix_instance_tenant_user", def = "{'tenantId': 1, 'userId': 1, 'status': 1}")
@CompoundIndex(name = "ix_instance_journey", def = "{'journeyId': 1, 'status': 1}")
public class JourneyInstance {

    @Id
    private String id;

    /** Tenant who owns this instance. */
    @Indexed
    private String tenantId;

    /** User who is executing this journey. */
    @Indexed
    private String userId;

    /** Reference to the journey definition ID. */
    @Indexed
    private String journeyId;

    /** Reference to the journey definition version used at start time. */
    private int journeyVersion;

    /**
     * ID of the step the user is currently on.
     * Null when the journey has not started or has completed.
     */
    private String currentStepId;

    /**
     * Arbitrary state collected during the journey (form answers, API responses, etc.).
     * This is the "working memory" for the journey.
     */
    private Map<String, Object> state;

    /**
     * Instance lifecycle status.
     * - ACTIVE: user is currently on a step
     * - WAITING: waiting for an async event (e.g. OTP, callback)
     * - COMPLETED: user finished the journey successfully
     * - ABANDONED: user stopped interacting (timeout)
     * - FAILED: a step error caused the journey to fail
     */
    @Builder.Default
    private InstanceStatus status = InstanceStatus.ACTIVE;

    /** When the journey was started. */
    @CreatedDate
    private Instant startedAt;

    /** When the journey was completed, abandoned, or failed. */
    private Instant completedAt;

    /**
     * Record of every step visit.
     * Appended to as the user advances.
     */
    @Builder.Default
    private List<HistoryEntry> history = new java.util.ArrayList<>();

    /** ID of the step that caused failure (if status == FAILED). */
    private String failedAtStepId;

    /** Error message if the journey failed. */
    private String failureReason;

    /** Client IP address at start time. */
    private String ipAddress;

    /** User agent string at start time. */
    private String userAgent;

    @LastModifiedDate
    private Instant updatedAt;

    public enum InstanceStatus {
        ACTIVE,
        WAITING,
        COMPLETED,
        ABANDONED,
        FAILED
    }

    /**
     * Add a history entry for a step visit.
     */
    public void addHistoryEntry(HistoryEntry entry) {
        if (this.history == null) {
            this.history = new java.util.ArrayList<>();
        }
        this.history.add(entry);
    }
}
