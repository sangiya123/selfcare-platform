package com.omobio.journey.web;

import com.omobio.journey.domain.JourneyInstance;
import com.omobio.journey.service.JourneyService;
import com.omobio.journey.service.JourneyService.StepResult;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Customer-facing journey controller.
 *
 * Endpoints for users to start, advance, and monitor their journey instances.
 *
 * @see JourneyService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;

    /**
     * List all journeys available to the current tenant.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<?>>> listJourneys() {
        return ResponseEntity.ok(ApiResponse.of(journeyService.listJourneys()));
    }

    /**
     * Get a specific journey definition.
     */
    @GetMapping("/{journeyId}")
    public ResponseEntity<ApiResponse<?>>
    getJourney(@PathVariable String journeyId) {
        return ResponseEntity.ok(ApiResponse.of(journeyService.getJourney(journeyId)));
    }

    /**
     * Start a new journey instance for the authenticated user.
     *
     * @param journeyId the journey to start
     * @return the created journey instance
     */
    @PostMapping("/{journeyId}/start")
    public ResponseEntity<ApiResponse<JourneyInstance>>
    startJourney(@PathVariable String journeyId, HttpServletRequest request) {
        String userId = TenantContext.get().getUserId();
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        log.info("Starting journey: journeyId={}, userId={}", journeyId, userId);
        JourneyInstance instance = journeyService.startJourney(journeyId, userId, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.of(instance));
    }

    /**
     * Get the current step of an active journey instance.
     */
    @GetMapping("/instances/{instanceId}/current-step")
    public ResponseEntity<ApiResponse<StepResult>> getCurrentStep(@PathVariable String instanceId) {
        return ResponseEntity.ok(ApiResponse.of(journeyService.getCurrentStep(instanceId)));
    }

    /**
     * Advance a journey instance from the current step.
     *
     * @param instanceId the instance to advance
     * @param inputData  data from the current step (form fields, API result, etc.)
     * @return the updated instance
     */
    @PostMapping("/instances/{instanceId}/advance")
    public ResponseEntity<ApiResponse<JourneyInstance>>
    advanceStep(@PathVariable String instanceId,
                @RequestBody(required = false) Map<String, Object> inputData) {
        log.info("Advancing journey step: instanceId={}", instanceId);
        JourneyInstance updated = journeyService.advanceStep(instanceId, inputData);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    /**
     * Abandon a journey instance.
     */
    @PostMapping("/instances/{instanceId}/abandon")
    public ResponseEntity<ApiResponse<Void>>
    abandonJourney(@PathVariable String instanceId,
                  @RequestParam(required = false) String reason) {
        log.info("Abandoning journey: instanceId={}, reason={}", instanceId, reason);
        journeyService.abandonJourney(instanceId, reason);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    /**
     * List journey instances for the current user.
     *
     * @param journeyId optional filter by journey
     * @param status    optional filter by status
     */
    @GetMapping("/instances")
    public ResponseEntity<ApiResponse<List<?>>> listInstances(
            @RequestParam(required = false) String journeyId,
            @RequestParam(required = false) String status) {
        JourneyInstance.InstanceStatus statusEnum = status != null
                ? JourneyInstance.InstanceStatus.valueOf(status.toUpperCase())
                : null;
        return ResponseEntity.ok(ApiResponse.of(
                journeyService.listInstances(journeyId, statusEnum)));
    }

    private static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        return xForwardedFor != null ? xForwardedFor.split(",")[0].trim() : request.getRemoteAddr();
    }
}
