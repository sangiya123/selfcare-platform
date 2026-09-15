package com.selfcare.journey.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A single step within a {@link JourneyDefinition}.
 *
 * Step types:
 * - FORM: collect user input
 * - CALL_API: invoke a backend service
 * - NAVIGATE: redirect to a screen
 * - BRANCH: conditional routing based on data/conditions
 * - WAIT: pause and poll/resume later
 *
 * Only registered step types can be used (ADR-009: no arbitrary code execution).
 *
 * @see JourneyDefinition
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyStep {

    /** Unique identifier within the journey definition. */
    private String stepId;

    /**
     * Step type — determines how the engine processes this step.
     * Must be one of the registered step types.
     */
    private StepType type;

    /** Display title shown to the user. */
    private String title;

    /** Detailed description or instruction. */
    private String description;

    /**
     * Step configuration — content is type-specific.
     * e.g. for FORM: field definitions; for CALL_API: endpoint + method + body template
     */
    private Map<String, Object> config;

    /**
     * IDs of steps that can follow this one.
     * The engine selects the appropriate next step based on conditions or user choice.
     */
    private List<String> nextStepIds;

    /**
     * Conditions under which each next step is taken.
     * Evaluated in order; first matching condition wins.
     * Format: List of { "condition": "expression", "nextStepId": "step-2" }
     */
    private List<StepCondition> conditions;

    /**
     * Whether this step must be completed before proceeding.
     * Default: true
     */
    @Builder.Default
    private boolean required = true;

    /**
     * Step timeout in seconds. If the step is not completed within this time,
     * the journey instance is automatically abandoned.
     */
    private Integer timeoutSeconds;

    /**
     * Fallback step ID to use if this step times out.
     */
    private String timeoutFallbackStepId;

    /**
     * Compensation/rollback step ID for this step.
     * Invoked if a later step fails and the journey needs to unwind.
     */
    private String compensationStepId;

    /** Tags for analytics and filtering. */
    private List<String> tags;

    public enum StepType {
        FORM,
        CALL_API,
        NAVIGATE,
        BRANCH,
        WAIT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepCondition {
        /** SpEL expression evaluated against the current journey state. */
        private String expression;

        /** Step ID to advance to if this condition matches. */
        private String nextStepId;

        /** Human-readable description of this condition for debugging. */
        private String description;
    }
}
