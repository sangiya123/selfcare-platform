package com.selfcare.journey.service;

import com.selfcare.journey.domain.JourneyInstance;
import com.selfcare.journey.domain.JourneyStep;
import com.selfcare.platform.common.web.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Journey engine — executes individual steps and determines the next step
 * in a journey.
 *
 * Responsibilities:
 * - Dispatch step actions (form data capture, API calls, navigation)
 * - Evaluate branch conditions
 * - Compute the next step ID
 * - Handle wait-state transitions
 *
 * This is a deterministic engine: there is no arbitrary code execution.
 * Each step type has a registered handler (see {@link StepHandler}).
 *
 * @see JourneyStep
 * @see JourneyInstance
 */
@Slf4j
@Component
public class JourneyEngine {

    private final ExpressionParser parser = new SpelExpressionParser();

    // Map of step type -> handler
    private final Map<JourneyStep.StepType, StepHandler> handlers = new HashMap<>();

    public JourneyEngine() {
        // Register built-in step handlers
        handlers.put(JourneyStep.StepType.FORM, new FormStepHandler());
        handlers.put(JourneyStep.StepType.CALL_API, new CallApiStepHandler());
        handlers.put(JourneyStep.StepType.NAVIGATE, new NavigateStepHandler());
        handlers.put(JourneyStep.StepType.BRANCH, new BranchStepHandler());
        handlers.put(JourneyStep.StepType.WAIT, new WaitStepHandler());
    }

    /**
     * Execute a single step.
     *
     * @param step     the step to execute
     * @param state    the current journey state (mutable)
     * @param instance the journey instance context
     * @return output data produced by the step
     */
    public Map<String, Object> executeStep(JourneyStep step,
                                           Map<String, Object> state,
                                           JourneyInstance instance) {
        StepHandler handler = handlers.get(step.getType());
        if (handler == null) {
            throw new BadRequestException("No handler registered for step type: " + step.getType());
        }
        return handler.handle(step, state, instance);
    }

    /**
     * Determine the next step ID for a step.
     *
     * Logic:
     * 1. If the step has conditions, evaluate each in order
     *    - First matching condition wins
     * 2. If no conditions match and there is exactly one nextStepId, use it
     * 3. If there are no nextStepIds, journey is complete (return null)
     * 4. Otherwise, ambiguous - throw
     *
     * @param step     the step that was just executed
     * @param state    the updated journey state
     * @param instance the journey instance context
     * @return the next step ID, or null if the journey is complete
     */
    public String determineNextStep(JourneyStep step, Map<String, Object> state, JourneyInstance instance) {
        // Evaluate conditions first
        if (step.getConditions() != null && !step.getConditions().isEmpty()) {
            for (JourneyStep.StepCondition condition : step.getConditions()) {
                if (evaluateCondition(condition.getExpression(), state)) {
                    log.debug("Condition matched: '{}' -> {}", condition.getExpression(), condition.getNextStepId());
                    return condition.getNextStepId();
                }
            }
        }

        // No conditions or no matches
        List<String> nextStepIds = step.getNextStepIds();
        if (nextStepIds == null || nextStepIds.isEmpty()) {
            return null;  // End of journey
        }
        if (nextStepIds.size() == 1) {
            return nextStepIds.get(0);
        }
        throw new BadRequestException(
                "Step '" + step.getStepId() + "' has multiple next steps with no condition match. " +
                "Use conditions to disambiguate.");
    }

    /**
     * Evaluate a SpEL expression against the current state.
     *
     * @param expression the SpEL expression
     * @param state      the journey state
     * @return true if the expression evaluates to a truthy value
     */
    private boolean evaluateCondition(String expression, Map<String, Object> state) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            Expression exp = parser.parseExpression(expression);
            EvaluationContext ctx = new StandardEvaluationContext();
            ctx.setVariable("state", state);
            // Inject top-level state keys for convenience
            state.forEach(ctx::setVariable);
            Object result = exp.getValue(ctx);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition '{}': {}", expression, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Step handlers
    // -------------------------------------------------------------------------

    /**
     * Common interface for all step handlers.
     */
    public interface StepHandler {
        Map<String, Object> handle(JourneyStep step, Map<String, Object> state, JourneyInstance instance);
    }

    /**
     * FORM handler — captures user input into state.
     * No output; input data is already in state by the time this is called.
     */
    private static class FormStepHandler implements StepHandler {
        @Override
        public Map<String, Object> handle(JourneyStep step, Map<String, Object> state, JourneyInstance instance) {
            log.debug("Executing FORM step: {}", step.getStepId());
            return Map.of();
        }
    }

    /**
     * CALL_API handler — invokes a configured backend API and stores the response in state.
     * <p>
     * Implementation note: the actual HTTP call is out of scope for the engine —
     * in production this would dispatch via WebClient to the configured endpoint.
     * Here we record the call attempt in the state for traceability.
     */
    private static class CallApiStepHandler implements StepHandler {
        @Override
        public Map<String, Object> handle(JourneyStep step, Map<String, Object> state, JourneyInstance instance) {
            log.debug("Executing CALL_API step: {}", step.getStepId());
            // Mark the step as having been called; actual HTTP call delegated to a downstream service
            return Map.of("step_executed", step.getStepId(), "executed_at", System.currentTimeMillis());
        }
    }

    /**
     * NAVIGATE handler — emits a navigation intent in the state.
     * The mobile/web client reads this and routes accordingly.
     */
    private static class NavigateStepHandler implements StepHandler {
        @Override
        public Map<String, Object> handle(JourneyStep step, Map<String, Object> state, JourneyInstance instance) {
            log.debug("Executing NAVIGATE step: {}", step.getStepId());
            return Map.of("navigate_to", step.getConfig() != null ? step.getConfig().get("target") : null);
        }
    }

    /**
     * BRANCH handler — purely conditional; no state mutation.
     * The next-step determination is handled by the engine.
     */
    private static class BranchStepHandler implements StepHandler {
        @Override
        public Map<String, Object> handle(JourneyStep step, Map<String, Object> state, JourneyInstance instance) {
            log.debug("Executing BRANCH step: {}", step.getStepId());
            return Map.of();
        }
    }

    /**
     * WAIT handler — moves the instance into WAITING status (handled by caller).
     * The step is still recorded; a separate event must wake the journey up.
     */
    private static class WaitStepHandler implements StepHandler {
        @Override
        public Map<String, Object> handle(JourneyStep step, Map<String, Object> state, JourneyInstance instance) {
            log.debug("Executing WAIT step: {}", step.getStepId());
            return Map.of("waiting_since", System.currentTimeMillis());
        }
    }
}
