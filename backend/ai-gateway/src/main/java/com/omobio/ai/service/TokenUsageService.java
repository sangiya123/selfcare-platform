package com.omobio.ai.service;

import com.omobio.ai.service.AIResponse.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Token Usage Service — tracks and limits AI token consumption per tenant and user.
 *
 * Responsibilities:
 * - Record token usage after each LLM call
 * - Enforce per-tenant rate limits (requests/min and tokens/min)
 * - Enforce per-user rate limits
 * - Report usage metrics for billing
 * - Track which models are used
 *
 * Storage: Redis (hot) + periodic flush to MySQL for durability.
 *
 * Pricing (per 1M tokens, USD):
 *   claude-sonnet-4-5:    $3.00 input / $15.00 output
 *   gpt-4o-mini:         $0.150 input / $0.600 output
 *   text-embedding-3-sm:  $0.020 / $0.020 (flat)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "omobio:ai:ratelimit:";
    private static final String USAGE_PREFIX = "omobio:ai:usage:";

    // Default limits (can be overridden per tenant in config)
    private static final int DEFAULT_TENANT_RPM = 60;   // requests per minute
    private static final int DEFAULT_TENANT_TPM = 60_000; // tokens per minute
    private static final int DEFAULT_USER_RPM = 20;    // requests per minute per user

    // Pricing (USD per 1M tokens)
    private static final BigDecimal CLAUDE_INPUT_PRICE = new BigDecimal("3.00");
    private static final BigDecimal CLAUDE_OUTPUT_PRICE = new BigDecimal("15.00");
    private static final BigDecimal GPT_INPUT_PRICE = new BigDecimal("0.15");
    private static final BigDecimal GPT_OUTPUT_PRICE = new BigDecimal("0.60");

    // -------------------------------------------------------------------------
    // Rate limiting
    // -------------------------------------------------------------------------

    /**
     * Check if a tenant is within their rate limit for requests/minute.
     *
     * @param tenantId the tenant
     * @return true if allowed, false if rate limited
     */
    public boolean checkTenantRateLimit(String tenantId) {
        String key = RATE_LIMIT_PREFIX + "tenant:rpm:" + tenantId + ":" + currentMinute();
        return incrementAndCheck(key, DEFAULT_TENANT_RPM);
    }

    /**
     * Check if a tenant is within their rate limit for tokens/minute.
     *
     * @param tenantId the tenant
     * @param tokenCount tokens being consumed
     * @return true if allowed, false if rate limited
     */
    public boolean checkTenantTokenLimit(String tenantId, int tokenCount) {
        String key = RATE_LIMIT_PREFIX + "tenant:tpm:" + tenantId + ":" + currentMinute();
        // Add token count to the current counter
        Long current = redisTemplate.opsForValue().increment(key, tokenCount);
        redisTemplate.expire(key, Duration.ofMinutes(2));
        int limit = DEFAULT_TENANT_TPM;
        return current == null || current <= limit;
    }

    /**
     * Check if a user is within their per-user rate limit.
     */
    public boolean checkUserRateLimit(String tenantId, String userId) {
        String key = RATE_LIMIT_PREFIX + "user:rpm:" + tenantId + ":" + userId + ":" + currentMinute();
        return incrementAndCheck(key, DEFAULT_USER_RPM);
    }

    /**
     * Record a rate limit violation in Redis for monitoring.
     */
    public void recordRateLimitViolation(String tenantId, String userId, String limitType) {
        String key = "omobio:ai:ratelimit:violations:" + LocalDate.now() + ":" + tenantId;
        redisTemplate.opsForHash().increment(key, limitType + ":" + (userId != null ? userId : "anon"), 1);
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    // -------------------------------------------------------------------------
    // Usage recording
    // -------------------------------------------------------------------------

    /**
     * Record token usage after an LLM call.
     *
     * @param tenantId tenant
     * @param userId user (nullable for anonymous)
     * @param model model name
     * @param usage token breakdown
     */
    public void recordUsage(String tenantId, String userId, String model, TokenUsage usage) {
        if (usage == null) return;

        String date = LocalDate.now().toString();
        String tenantKey = USAGE_PREFIX + "tenant:" + date + ":" + tenantId;
        String userKey = USAGE_PREFIX + "user:" + date + ":" + tenantId + ":" + (userId != null ? userId : "anon");
        String modelKey = USAGE_PREFIX + "model:" + date + ":" + model;

        // Increment token counters
        redisTemplate.opsForHash().increment(tenantKey, "inputTokens", usage.getPromptTokens());
        redisTemplate.opsForHash().increment(tenantKey, "outputTokens", usage.getCompletionTokens());
        redisTemplate.opsForHash().increment(tenantKey, "totalTokens", usage.getTotalTokens());
        redisTemplate.opsForHash().increment(tenantKey, "requestCount", 1);
        redisTemplate.expire(tenantKey, Duration.ofDays(32));

        if (userId != null) {
            redisTemplate.opsForHash().increment(userKey, "inputTokens", usage.getPromptTokens());
            redisTemplate.opsForHash().increment(userKey, "outputTokens", usage.getCompletionTokens());
            redisTemplate.opsForHash().increment(userKey, "totalTokens", usage.getTotalTokens());
            redisTemplate.opsForHash().increment(userKey, "requestCount", 1);
            redisTemplate.expire(userKey, Duration.ofDays(32));
        }

        redisTemplate.opsForHash().increment(modelKey, "requests", 1);
        redisTemplate.opsForHash().increment(modelKey, "inputTokens", usage.getPromptTokens());
        redisTemplate.opsForHash().increment(modelKey, "outputTokens", usage.getCompletionTokens());
        redisTemplate.expire(modelKey, Duration.ofDays(32));

        log.debug("Usage recorded: tenant={}, user={}, model={}, tokens={}",
                tenantId, userId, model, usage.getTotalTokens());
    }

    /**
     * Estimate cost in USD for a token usage record.
     */
    public BigDecimal estimateCost(String model, TokenUsage usage) {
        if (usage == null) return BigDecimal.ZERO;

        boolean isClaude = model != null && model.toLowerCase().contains("claude");
        BigDecimal inputPrice = isClaude ? CLAUDE_INPUT_PRICE : GPT_INPUT_PRICE;
        BigDecimal outputPrice = isClaude ? CLAUDE_OUTPUT_PRICE : GPT_OUTPUT_PRICE;

        BigDecimal inputCost = inputPrice
                .multiply(new BigDecimal(usage.getPromptTokens()))
                .divide(new BigDecimal(1_000_000), 6, RoundingMode.HALF_UP);

        BigDecimal outputCost = outputPrice
                .multiply(new BigDecimal(usage.getCompletionTokens()))
                .divide(new BigDecimal(1_000_000), 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }

    /**
     * Get today's usage summary for a tenant.
     */
    public Map<Object, Object> getTenantUsageToday(String tenantId) {
        String key = USAGE_PREFIX + "tenant:" + LocalDate.now() + ":" + tenantId;
        Map<Object, Object> usage = redisTemplate.opsForHash().entries(key);
        return usage != null ? usage : Map.of();
    }

    /**
     * Get today's spend (USD) for a tenant — sum of all model costs incurred
     * today. Used by the AI governance service to enforce daily budgets.
     */
    public BigDecimal todaySpend(String tenantId) {
        Map<Object, Object> usage = getTenantUsageToday(tenantId);
        Object cost = usage.get("cost_usd");
        if (cost == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(cost.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean incrementAndCheck(String key, int limit) {
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(2));
        return count != null && count <= limit;
    }

    private String currentMinute() {
        return String.valueOf(Instant.now().getEpochSecond() / 60);
    }
}
