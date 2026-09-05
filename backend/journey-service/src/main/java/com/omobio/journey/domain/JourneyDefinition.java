package com.omobio.journey.domain;

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

/**
 * Journey definition — a reusable, versioned journey template owned by a tenant.
 *
 * Stored in MongoDB as the source of truth for journey configuration.
 * Compiled into an immutable runtime manifest at publish time.
 *
 * Lifecycle: DRAFT -> PUBLISHED -> ARCHIVED
 *
 * @see JourneyStep
 * @see com.omobio.journey.service.JourneyService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "journey_definitions")
@CompoundIndex(name = "ix_journey_tenant", def = "{'tenantId': 1, 'journeyId': 1}")
public class JourneyDefinition {

    @Id
    private String id;

    /** Tenant who owns this journey. */
    @Indexed
    private String tenantId;

    /**
     * Business identifier for the journey (e.g. "new-connection-flow").
     * Unique within a tenant.
     */
    @Indexed
    private String journeyId;

    /** Human-readable name. */
    private String name;

    /** Description of what this journey accomplishes. */
    private String description;

    /** Version number, incremented on each publish. */
    @Builder.Default
    private int version = 1;

    /**
     * Lifecycle status.
     * - DRAFT: editable, not runnable
     * - PUBLISHED: frozen, runnable
     * - ARCHIVED: not runnable, kept for history
     */
    @Builder.Default
    private JourneyStatus status = JourneyStatus.DRAFT;

    /** Ordered list of steps in this journey. */
    private List<JourneyStep> steps;

    /** Entry point step ID. */
    private String entryStepId;

    /** Tags for grouping/categorization. */
    private List<String> tags;

    /** Admin user who created this journey. */
    private String createdBy;

    /** Admin user who last updated this journey. */
    private String updatedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /** Timestamp when the journey was published. */
    private Instant publishedAt;

    public enum JourneyStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }
}
