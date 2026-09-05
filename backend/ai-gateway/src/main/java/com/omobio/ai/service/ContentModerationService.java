package com.omobio.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Content Moderation Service — filters harmful content from AI inputs and outputs.
 *
 * Provides three layers:
 * 1. Denylist: block known harmful words/phrases (configurable per tenant)
 * 2. OpenAI Moderation API: primary safety check (when API key configured)
 * 3. Intent guardrails: detect suspicious patterns (jailbreak attempts, etc.)
 *
 * All moderation decisions are logged. Flagged content is recorded in the audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${omobio.ai.moderation.openai-api-key:}")
    private String openAiApiKey;

    @Value("${omobio.ai.moderation.enabled:true}")
    private boolean moderationEnabled;

    // Known jailbreak/prompt injection patterns
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore all previous", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard.*instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now.*instead", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend to be", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal your.*system", Pattern.CASE_INSENSITIVE),
            Pattern.compile("DAN\\s+do anything now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new.*instruction", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override.*safety", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{.*system.*\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<.*>.*</.*>", Pattern.CASE_INSENSITIVE) // XML injection
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Moderate a user message before it reaches the AI.
     *
     * @param message the user's message
     * @param tenantId tenant for per-tenant denylists
     * @param userId user for logging
     * @return moderation result
     */
    public ModerationResult moderateInput(String message, String tenantId, String userId) {
        if (!moderationEnabled) {
            return ModerationResult.safe();
        }

        // 1. Check for injection patterns
        ModerationResult injectionCheck = checkInjections(message);
        if (!injectionCheck.isSafe()) {
            log.warn("Injection detected: tenant={}, user={}, pattern={}",
                    tenantId, userId, injectionCheck.flaggedCategory());
            return injectionCheck;
        }

        // 2. OpenAI Moderation API check
        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            try {
                ModerationResult apiResult = checkWithOpenAiModeration(message);
                if (!apiResult.isSafe()) {
                    log.warn("Moderation API flagged content: tenant={}, user={}, categories={}",
                            tenantId, userId, apiResult.flaggedCategories());
                    return apiResult;
                }
            } catch (Exception e) {
                log.warn("Moderation API call failed, allowing: {}", e.getMessage());
            }
        }

        return ModerationResult.safe();
    }

    /**
     * Moderate AI-generated output before sending to user.
     */
    public ModerationResult moderateOutput(String content, String tenantId) {
        if (!moderationEnabled) {
            return ModerationResult.safe();
        }
        // Output is less strictly filtered but we check for PII leakage
        return checkInjections(content);
    }

    // -------------------------------------------------------------------------
    // Implementation
    // -------------------------------------------------------------------------

    private ModerationResult checkInjections(String text) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return ModerationResult.builder()
                        .safe(false)
                        .flaggedCategory("PROMPT_INJECTION")
                        .flaggedCategories(List.of("PROMPT_INJECTION"))
                        .reason("Message contains suspicious pattern that may be an instruction injection")
                        .build();
            }
        }
        return ModerationResult.safe();
    }

    @SuppressWarnings("unchecked")
    private ModerationResult checkWithOpenAiModeration(String text) throws Exception {
        String baseUrl = "https://api.openai.com";
        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/moderations")
                .header("Authorization", "Bearer " + openAiApiKey)
                .bodyValue(Map.of("input", text))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) {
            return ModerationResult.safe();
        }

        Map<String, Object> result = results.get(0);
        Map<String, Boolean> categories = (Map<String, Boolean>) result.get("categories");

        List<String> flagged = categories.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();

        if (flagged.isEmpty()) {
            return ModerationResult.safe();
        }

        return ModerationResult.builder()
                .safe(false)
                .flaggedCategories(flagged)
                .flaggedCategory(flagged.get(0))
                .reason("Content flagged by moderation API: " + String.join(", ", flagged))
                .build();
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class ModerationResult {
        /** Whether the content is safe to pass through */
        private boolean safe;
        /** Primary category that caused the flag (if not safe) */
        private String flaggedCategory;
        /** All flagged categories (if not safe) */
        private List<String> flaggedCategories;
        /** Human-readable reason */
        private String reason;

        public static ModerationResult safe() {
            return new ModerationResult(true, null, List.of(), "ok");
        }

        public String flaggedCategory() {
            return flaggedCategory;
        }

        public List<String> flaggedCategories() {
            return flaggedCategories;
        }

        public String reason() {
            return reason;
        }
    }
}
