package com.omobio.journey.web;

import com.omobio.journey.domain.JourneyDefinition;
import com.omobio.journey.service.JourneyService;
import com.omobio.platform.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-facing journey management controller.
 *
 * Endpoints for studio admins to create, update, publish, and archive journeys.
 *
 * @see JourneyService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/journeys")
@RequiredArgsConstructor
public class AdminJourneyController {

    private final JourneyService journeyService;

    /**
     * List all journey definitions for the current tenant (all statuses).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<?>>> listJourneys() {
        return ResponseEntity.ok(ApiResponse.of(journeyService.listJourneys()));
    }

    /**
     * Get a specific journey definition by business ID.
     */
    @GetMapping("/{journeyId}")
    public ResponseEntity<ApiResponse<?>> getJourney(@PathVariable String journeyId) {
        return ResponseEntity.ok(ApiResponse.of(journeyService.getJourney(journeyId)));
    }

    /**
     * Create a new journey definition (in DRAFT status).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<JourneyDefinition>>
    createJourney(@RequestBody JourneyDefinition definition) {
        log.info("Creating journey: {}", definition.getJourneyId());
        JourneyDefinition created = journeyService.createJourney(definition);
        return ResponseEntity.ok(ApiResponse.of(created));
    }

    /**
     * Update a journey definition (only DRAFT journeys can be updated).
     */
    @PutMapping("/{journeyId}")
    public ResponseEntity<ApiResponse<JourneyDefinition>>
    updateJourney(@PathVariable String journeyId,
                 @RequestBody JourneyDefinition updated) {
        log.info("Updating journey: {}", journeyId);
        JourneyDefinition result = journeyService.updateJourney(journeyId, updated);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    /**
     * Publish a journey — makes it immutable and available for users to start.
     */
    @PostMapping("/{journeyId}/publish")
    public ResponseEntity<ApiResponse<JourneyDefinition>>
    publishJourney(@PathVariable String journeyId) {
        log.info("Publishing journey: {}", journeyId);
        JourneyDefinition published = journeyService.publishJourney(journeyId);
        return ResponseEntity.ok(ApiResponse.of(published));
    }

    /**
     * Archive a journey — marks it as non-runnable but keeps it for historical reference.
     */
    @PostMapping("/{journeyId}/archive")
    public ResponseEntity<ApiResponse<JourneyDefinition>>
    archiveJourney(@PathVariable String journeyId) {
        log.info("Archiving journey: {}", journeyId);
        JourneyDefinition archived = journeyService.archiveJourney(journeyId);
        return ResponseEntity.ok(ApiResponse.of(archived));
    }
}
