package com.selfcare.journey.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * A single record of a step visit within a {@link JourneyInstance}.
 *
 * Append-only: history entries are never modified after they are recorded.
 *
 * @see JourneyInstance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryEntry {

    /** The step ID that was visited. */
    private String stepId;

    /** The type of the step that was visited. */
    private String stepType;

    /** When the step was entered. */
    private Instant enteredAt;

    /** When the step was completed (left). */
    private Instant exitedAt;

    /**
     * Outcome of visiting this step.
     * e.g. "COMPLETED", "TIMEOUT", "SKIPPED", "ERROR"
     */
    @Builder.Default
    private String outcome = "COMPLETED";

    /** The input data provided when advancing from this step. */
    private Map<String, Object> inputData;

    /** The output data produced by this step (API response, computed values). */
    private Map<String, Object> outputData;

    /**
     * Error details if the step failed.
     */
    private String errorMessage;

    /** Duration in milliseconds for this step visit. */
    private Long durationMs;

    /** Which step was visited next (for audit/debugging). */
    private String nextStepId;

    /** Optional analytics event name associated with this step. */
    private String analyticsEvent;
}
