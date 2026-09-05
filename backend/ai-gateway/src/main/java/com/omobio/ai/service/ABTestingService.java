package com.omobio.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * A/B Testing Service — for prompt template experiments.
 *
 * Allows admins to define traffic splits across prompt variants and
 * automatically assigns users to variants based on a stable hash
 * (userId + experimentId). This ensures the same user always sees
 * the same variant during the experiment.
 *
 * Use cases:
 *   - Comparing two prompt strategies
 *   - Trying a new LLM model on a subset of traffic
 *   - Testing a different temperature or other parameters
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ABTestingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String EXPERIMENT_PREFIX = "omobio:ai:experiment:";
    private static final String ASSIGNMENT_PREFIX = "omobio:ai:assignment:";

    /**
     * Get the variant assignment for a user in an experiment.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID (must be stable)
     * @return the assigned variant, or null if no experiment is active
     */
    public String getVariant(String experimentId, String userId) {
        if (userId == null || userId.isBlank()) return null;

        // Check if experiment is active
        Experiment experiment = getExperiment(experimentId);
        if (experiment == null || !experiment.active) return null;

        // Check if we already assigned this user
        String cacheKey = ASSIGNMENT_PREFIX + experimentId + ":" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String s) return s;

        // Assign deterministically
        String variant = assignVariant(experiment, userId);
        redisTemplate.opsForValue().set(cacheKey, variant,
                java.time.Duration.ofDays(30));
        log.info("Assigned user to variant: experiment={}, user={}, variant={}",
                experimentId, userId, variant);
        return variant;
    }

    /**
     * Get the prompt template ID for a user based on A/B test assignment.
     *
     * @param baseTemplateId the default template
     * @param userId the user
     * @return either the base template or an experiment variant
     */
    public String resolveTemplateId(String baseTemplateId, String userId) {
        String variant = getVariant("prompt-" + baseTemplateId, userId);
        if (variant == null) return baseTemplateId;
        return variant.equals("control") ? baseTemplateId : baseTemplateId + "-" + variant;
    }

    /**
     * Create or update an experiment.
     */
    public void saveExperiment(Experiment experiment) {
        String key = EXPERIMENT_PREFIX + experiment.experimentId;
        redisTemplate.opsForValue().set(key, experiment,
                java.time.Duration.ofDays(experiment.durationDays));
        log.info("Saved experiment: {}", experiment.experimentId);
    }

    /**
     * Deactivate an experiment.
     */
    public void stopExperiment(String experimentId) {
        Experiment experiment = getExperiment(experimentId);
        if (experiment != null) {
            experiment.active = false;
            saveExperiment(experiment);
            log.info("Stopped experiment: {}", experimentId);
        }
    }

    /**
     * Get experiment definition.
     */
    public Experiment getExperiment(String experimentId) {
        Object cached = redisTemplate.opsForValue().get(EXPERIMENT_PREFIX + experimentId);
        if (cached instanceof Experiment e) return e;
        return null;
    }

    /**
     * Compute the variant based on a stable hash of userId + experimentId.
     */
    private String assignVariant(Experiment experiment, String userId) {
        try {
            String input = experiment.experimentId + ":" + userId;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());

            // Convert first 4 bytes to long for bucketing
            long bucket = ((hash[0] & 0xFFL) << 24)
                    | ((hash[1] & 0xFFL) << 16)
                    | ((hash[2] & 0xFFL) << 8)
                    | (hash[3] & 0xFFL);
            // Normalize to 0–9999
            long normalized = Math.abs(bucket) % 10000;

            // Map to variants based on weights
            long cumulative = 0;
            for (Variant v : experiment.variants) {
                cumulative += v.weight * 100; // weights are 0–100
                if (normalized < cumulative) {
                    return v.name;
                }
            }
            return experiment.variants.get(0).name; // fallback
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return experiment.variants.get(0).name;
        }
    }

    // -------------------------------------------------------------------------
    // Types
    // -------------------------------------------------------------------------

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class Experiment {
        private String experimentId;
        private String name;
        private String description;
        private boolean active;
        private int durationDays;
        private List<Variant> variants;
    }

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class Variant {
        /** Variant name (e.g., "control", "treatment-a") */
        private String name;
        /** Weight 0-100 (must sum to 100 across all variants) */
        private int weight;
        /** Optional metadata: prompt template ID, model name, parameters */
        private Map<String, String> config;
    }
}
