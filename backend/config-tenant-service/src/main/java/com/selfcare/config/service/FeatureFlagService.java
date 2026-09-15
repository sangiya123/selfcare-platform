package com.selfcare.config.service;

import com.selfcare.config.domain.FeatureFlag;
import com.selfcare.config.repository.FeatureFlagRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ConflictException;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feature flag service — manages flag definitions and rollout state.
 *
 * Four-eyes approval (FEATURE_ENABLE_100):
 *   Enabling a flag to 100% rollout is a high-risk action. It removes
 *   the safety of staged rollouts — a buggy flag at 100% affects all
 *   users immediately. The service gates changes to 100% through the
 *   approval-service.
 *
 *   Changes to other values (0%-99% or stays at 100%) do NOT require approval.
 *
 * Rollout evaluation:
 *   Flags are evaluated client-side by the SDK, using a hash of
 *   (tenantId, flagName, userId) to determine inclusion in the rollout %.
 *   The server can also evaluate via the config-bff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository flagRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApprovalClient approvalClient;

    /** Cache key for flag lookups. */
    private static final String FLAG_CACHE_PREFIX = "selfcare:flag:";

    /** Maximum wait for synchronous approval decision. */
    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(5);

    /** Polling interval. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    public List<FeatureFlag> listForTenant(String tenantId) {
        return flagRepository.findByTenantId(tenantId);
    }

    public FeatureFlag get(String id) {
        return flagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FeatureFlag", id));
    }

    public FeatureFlag getByName(String tenantId, String name) {
        return flagRepository.findByTenantIdAndName(tenantId, name)
                .orElseThrow(() -> new NotFoundException("FeatureFlag", name));
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Create or update a feature flag.
     *
     * If the resulting rollout is 100% AND it was previously less than 100%,
     * the change is gated by four-eyes approval. The call blocks until an
     * approver decides.
     */
    public FeatureFlag save(FeatureFlag flag) {
        boolean isNew = flag.getId() == null;
        Integer previousRollout = null;

        if (!isNew) {
            FeatureFlag existing = get(flag.getId());
            previousRollout = existing.getRolloutPercentage();
        }

        // Detect escalation to 100%
        if (!isNew && previousRollout != null
                && previousRollout < 100
                && flag.getRolloutPercentage() == 100) {
            return escalateToHundredWithApproval(flag, previousRollout);
        }

        if (isNew && flag.getRolloutPercentage() == 100) {
            // New flag created at 100% — also requires approval
            return escalateToHundredWithApproval(flag, 0);
        }

        return persist(flag);
    }

    /**
     * Delete a feature flag.
     */
    public void delete(String id) {
        FeatureFlag flag = get(id);
        flagRepository.delete(flag);
        invalidateCache(flag);
    }

    // -------------------------------------------------------------------------
    // Internal: escalation flow
    // -------------------------------------------------------------------------

    /**
     * Gated change to 100% rollout.
     *
     * 1. Submit change for four-eyes approval (FEATURE_ENABLE_100 action)
     * 2. Block and wait for terminal decision
     * 3. APPROVED -> persist the change
     * 4. Anything else -> throw 409 Conflict
     */
    private FeatureFlag escalateToHundredWithApproval(FeatureFlag flag, int previousRollout) {
        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("flagId", flag.getId());
        snapshot.put("flagName", flag.getName());
        snapshot.put("previousRollout", previousRollout);
        snapshot.put("newRollout", flag.getRolloutPercentage());
        snapshot.put("environment",
                flag.getEnabledEnvironments() != null
                        ? flag.getEnabledEnvironments()
                        : List.of());

        String requestId = approvalClient.submitForApproval(
                        tenantId,
                        "FEATURE_ENABLE_100",
                        "feature_flag",
                        flag.getId(),
                        userId,
                        userId,
                        snapshot)
                .block(APPROVAL_TIMEOUT);

        if (requestId == null) {
            throw new ConflictException("FEATURE_ENABLE_100",
                    "Could not submit flag for approval — approval-service is unavailable");
        }

        log.info("Flag escalation submitted: flagId={}, name={}, requestId={}",
                flag.getId(), flag.getName(), requestId);

        String finalStatus = waitForDecision(requestId, APPROVAL_TIMEOUT);
        if (!"APPROVED".equals(finalStatus)) {
            throw new ConflictException("FEATURE_ENABLE_100",
                    "Feature flag 100% rollout was " + finalStatus + " — no change applied");
        }

        return persist(flag);
    }

    private FeatureFlag persist(FeatureFlag flag) {
        Instant now = Instant.now();
        if (flag.getId() == null) {
            flag.setId(UUID.randomUUID().toString());
        }
        if (flag.getCreatedAt() == null) {
            flag.setCreatedAt(now);
            flag.setCreatedBy(TenantContext.get().getUserId());
        }
        flag.setUpdatedAt(now);

        FeatureFlag saved = flagRepository.save(flag);
        invalidateCache(saved);
        log.info("Feature flag saved: id={}, name={}, rollout={}%",
                saved.getId(), saved.getName(), saved.getRolloutPercentage());
        return saved;
    }

    private String waitForDecision(String requestId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            java.util.Optional<String> status = approvalClient
                    .getRequestStatus(requestId)
                    .block(Duration.ofSeconds(5));
            if (status.isPresent()) {
                String s = status.get();
                if ("APPROVED".equals(s) || "REJECTED".equals(s)
                        || "CANCELLED".equals(s) || "EXPIRED".equals(s)) {
                    return s;
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
        }
        return "TIMEOUT";
    }

    private void invalidateCache(FeatureFlag flag) {
        String key = FLAG_CACHE_PREFIX + flag.getTenantId() + ":" + flag.getName();
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to invalidate flag cache: {}", e.getMessage());
        }
    }
}
