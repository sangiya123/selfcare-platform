package com.selfcare.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.selfcare.config.compiler.ConfigCompiler;
import com.selfcare.config.domain.LayoutDocument;
import com.selfcare.config.domain.ThemeDocument;
import com.selfcare.config.repository.LayoutDocumentRepository;
import com.selfcare.config.repository.ThemeDocumentRepository;
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
 * Layout service — manages layout documents, compiles them to immutable manifests,
 * and serves the published manifest for the mobile / web apps.
 *
 * Four-eyes approval (LAYOUT_PUBLISH):
 *   Publishing a layout is one of the 9 high-risk actions requiring four-eyes
 *   approval. {@link #publish(String)} now submits a request to the
 *   approval-service and blocks until a decision is made.
 *
 *   For synchronous approval (e.g. during admin-UI testing) the implementation
 *   uses a blocking flow: the caller waits for the request to reach a terminal
 *   state. The admin UI should instead redirect to the approval queue and
 *   notify the caller via webhook / event when the decision arrives.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LayoutService {

    private final LayoutDocumentRepository layoutRepository;
    private final ThemeDocumentRepository themeRepository;
    private final ConfigCompiler configCompiler;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApprovalClient approvalClient;

    private static final String MANIFEST_CACHE_PREFIX = "selfcare:layout:manifest:";
    private static final Duration MANIFEST_TTL = Duration.ofMinutes(30);

    /** Polling interval for synchronous publish + wait. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    /** Maximum wait for a synchronous approval decision. */
    private static final Duration PUBLISH_WAIT_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Get a published layout for a tenant + experience + profileKey.
     * Returns the compiled manifest (immutable, versioned).
     */
    public ConfigCompiler.CompiledManifest getPublishedManifest(
            String tenantId, String environment, String experience, String profileKey) {
        String cacheKey = MANIFEST_CACHE_PREFIX + tenantId + ":" + environment + ":" + experience + ":" + profileKey;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof ConfigCompiler.CompiledManifest m) {
            return m;
        }

        LayoutDocument doc = layoutRepository
                .findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
                        tenantId, environment, experience, profileKey, "PUBLISHED")
                .or(() -> layoutRepository
                        .findByTenantIdAndEnvironmentAndExperienceAndProfileKeyAndStatus(
                                tenantId, environment, experience, "default", "PUBLISHED"))
                .or(() -> firstPublishedForExperience(tenantId, environment, experience))
                .orElseThrow(() -> new NotFoundException("Layout",
                        tenantId + "/" + environment + "/" + experience + "/" + profileKey));

        ConfigCompiler.CompiledManifest manifest = configCompiler.compile(doc);
        redisTemplate.opsForValue().set(cacheKey, manifest, MANIFEST_TTL);
        return manifest;
    }

    /**
     * Best-effort fallback: the highest configVersion published layout for a
     * tenant + environment + experience when no profileKey matches. Keeps the
     * mobile app resolvable even when the requested profile key is not seeded.
     */
    private java.util.Optional<LayoutDocument> firstPublishedForExperience(
            String tenantId, String environment, String experience) {
        List<LayoutDocument> docs = layoutRepository
                .findByTenantIdAndEnvironmentAndExperienceAndStatusOrderByConfigVersionDesc(
                        tenantId, environment, experience, "PUBLISHED");
        return docs.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(docs.get(0));
    }

    /**
     * List all layouts for a tenant (any status).
     */
    public List<LayoutDocument> listForTenant(String tenantId) {
        return layoutRepository.findByTenantId(tenantId);
    }

    /**
     * List published layouts for a tenant.
     */
    public List<LayoutDocument> listPublished(String tenantId) {
        return layoutRepository.findByTenantIdAndStatus(tenantId, "PUBLISHED");
    }

    /**
     * Get a single layout document.
     */
    public LayoutDocument get(String id) {
        return layoutRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Layout", id));
    }

    /**
     * Create or update a layout (DRAFT).
     */
    public LayoutDocument save(LayoutDocument layout) {
        if (layout.getId() == null) {
            layout.setId(UUID.randomUUID().toString());
        }
        Instant now = Instant.now();
        if (layout.getCreatedAt() == null) {
            layout.setCreatedAt(now);
            layout.setCreatedBy(TenantContext.get().getUserId());
        }
        layout.setUpdatedAt(now);
        if (layout.getStatus() == null) layout.setStatus("DRAFT");
        if (layout.getConfigVersion() == 0) layout.setConfigVersion(1);

        LayoutDocument saved = layoutRepository.save(layout);
        invalidateCache(saved);
        log.info("Layout saved: tenant={}, experience={}, version={}, status={}",
                saved.getTenantId(), saved.getExperience(), saved.getConfigVersion(), saved.getStatus());
        return saved;
    }

    /**
     * Publish a layout (DRAFT -> PUBLISHED).
     *
     * Four-eyes approval:
     *   This is a LAYOUT_PUBLISH high-risk action. The publish is gated by
     *   the approval-service. The flow is:
     *
     *   1. Submit the change for approval
     *   2. Block and wait for a terminal decision
     *   3. If APPROVED, apply the publish
     *   4. If REJECTED / CANCELLED / EXPIRED, throw 409 Conflict
     *
     * The admin-UI asynchronous path (recommended for production): the
     * admin submits a publish and is redirected to the approval queue.
     * The actual publish runs when a second admin approves the request.
     *
     * @throws ConflictException if the approval is rejected, cancelled, or
     *                           expires before a decision
     */
    public LayoutDocument publish(String id) {
        LayoutDocument doc = get(id);
        if ("PUBLISHED".equals(doc.getStatus())) {
            return doc; // Already published — no-op
        }

        String tenantId = TenantContext.get().getTenantId();
        String userId = TenantContext.get().getUserId();

        // 0. Schema gate — authorable config must conform before entering the approval workflow
        try {
            configCompiler.validateDocument(doc);
        } catch (IllegalStateException e) {
            throw new ConflictException("LAYOUT_INVALID",
                    "Layout does not conform to schema: " + e.getMessage());
        }

        // 1. Submit for four-eyes approval
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("layoutId", doc.getId());
        snapshot.put("experience", doc.getExperience());
        snapshot.put("environment", doc.getEnvironment());
        snapshot.put("profileKey", doc.getProfileKey());
        snapshot.put("currentVersion", doc.getConfigVersion());
        snapshot.put("currentStatus", doc.getStatus());
        snapshot.put("sectionCount", doc.getSections() != null ? doc.getSections().size() : 0);

        String requestId = approvalClient.submitForApproval(
                        tenantId,
                        "LAYOUT_PUBLISH",
                        "layout",
                        doc.getId(),
                        userId,
                        userId,
                        snapshot)
                .block(PUBLISH_WAIT_TIMEOUT);

        if (requestId == null) {
            throw new ConflictException("LAYOUT_PUBLISH",
                    "Could not submit layout for approval — approval-service is unavailable");
        }

        log.info("Layout publish submitted for approval: layoutId={}, requestId={}",
                doc.getId(), requestId);

        // 2. Block and wait for terminal decision
        String finalStatus = waitForDecision(requestId, PUBLISH_WAIT_TIMEOUT);
        if (!"APPROVED".equals(finalStatus)) {
            throw new ConflictException("LAYOUT_PUBLISH",
                    "Layout publish was " + finalStatus + " — no change applied");
        }

        // 3. Apply the publish
        doc.setStatus("PUBLISHED");
        doc.setPublishedAt(Instant.now());
        doc.setPublishedBy(TenantContext.get().getUserId());
        doc.setConfigVersion(doc.getConfigVersion() + 1);
        LayoutDocument saved = layoutRepository.save(doc);
        invalidateCache(saved);
        log.info("Layout published after approval: tenant={}, experience={}, version={}",
                saved.getTenantId(), saved.getExperience(), saved.getConfigVersion());
        return saved;
    }

    /**
     * Block and poll until the approval request reaches a terminal state.
     *
     * @return The terminal status: APPROVED, REJECTED, CANCELLED, or EXPIRED
     */
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

    /**
     * Archive a layout.
     */
    public LayoutDocument archive(String id) {
        LayoutDocument doc = get(id);
        doc.setStatus("ARCHIVED");
        LayoutDocument saved = layoutRepository.save(doc);
        invalidateCache(saved);
        return saved;
    }

    public void delete(String id) {
        LayoutDocument doc = get(id);
        layoutRepository.delete(doc);
        invalidateCache(doc);
    }

    private void invalidateCache(LayoutDocument doc) {
        String pattern = MANIFEST_CACHE_PREFIX + doc.getTenantId() + ":*";
        // Best-effort cache invalidation
        redisTemplate.delete(pattern);
    }
}
