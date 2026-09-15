package com.selfcare.ai.service;

import com.selfcare.ai.domain.AiKillSwitch;
import com.selfcare.ai.domain.AiPromptVersion;
import com.selfcare.ai.domain.AiUseCase;
import com.selfcare.ai.repository.AiKillSwitchRepository;
import com.selfcare.ai.repository.AiPromptVersionRepository;
import com.selfcare.ai.repository.AiUseCaseRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * AI governance — implements the controls defined in
 * `Planning doc/06_ai/02_AI_Governance_Evaluation.md`.
 *
 *  - use-case owner, risk tier, approved models
 *  - operator/data-residency policy
 *  - tool allow list
 *  - human approval thresholds
 *  - retention policy
 *  - cost/token budget enforcement
 *  - incident / kill switch
 *
 * The AIModelGateway consults this service on every LLM call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGovernanceService {

    private final AiUseCaseRepository useCaseRepository;
    private final AiKillSwitchRepository killSwitchRepository;
    private final AiPromptVersionRepository promptVersionRepository;
    private final TokenUsageService tokenUsageService;

    private static final Set<String> PLATFORM_APPROVED_PROVIDERS = Set.of(
            "anthropic", "openai", "google"
    );

    // -------------------------------------------------------------------------
    // Use case registry
    // -------------------------------------------------------------------------

    @Transactional
    public AiUseCase registerUseCase(AiUseCase useCase) {
        validateUseCase(useCase);
        log.info("Registering AI use case: id={}, tier={}, tenant={}",
                useCase.getUseCaseId(), useCase.getRiskTier(), useCase.getTenantId());
        return useCaseRepository.save(useCase);
    }

    @Transactional
    public AiUseCase updateUseCase(AiUseCase useCase) {
        validateUseCase(useCase);
        return useCaseRepository.save(useCase);
    }

    @Transactional(readOnly = true)
    public Optional<AiUseCase> getUseCase(String useCaseId) {
        String tenantId = TenantContext.get().getTenantId();
        return useCaseRepository.findEffective(tenantId, useCaseId);
    }

    @Transactional(readOnly = true)
    public List<AiUseCase> listByRiskTier(String riskTier) {
        return useCaseRepository.findByRiskTier(riskTier);
    }

    private void validateUseCase(AiUseCase uc) {
        if (uc.getRiskTier() == null
                || !Set.of("LOW", "MEDIUM", "HIGH").contains(uc.getRiskTier())) {
            throw new IllegalArgumentException("riskTier must be LOW, MEDIUM, or HIGH");
        }
        if (uc.getApprovedProviders() == null || uc.getApprovedProviders().isBlank()) {
            throw new IllegalArgumentException("approvedProviders must be set");
        }
        // Validate each provider is in the platform allow list
        for (String p : uc.getApprovedProviders().split(",")) {
            String trimmed = p.trim();
            if (!PLATFORM_APPROVED_PROVIDERS.contains(trimmed)) {
                throw new IllegalArgumentException("Provider not on platform allow list: " + trimmed);
            }
        }
        if (uc.getRetentionDays() == null || uc.getRetentionDays() < 1 || uc.getRetentionDays() > 365) {
            throw new IllegalArgumentException("retentionDays must be between 1 and 365");
        }
    }

    // -------------------------------------------------------------------------
    // Pre-call policy check
    // -------------------------------------------------------------------------

    /**
     * Check whether a use case is allowed to run for a tenant, given the
     * current kill-switch and budget state. Throws on policy violation.
     *
     * @return effective AiUseCase, or null if no policy is registered
     */
    @Transactional(readOnly = true)
    public AiUseCase enforcePolicy(String useCaseId, String requestedProvider) {
        String tenantId = TenantContext.get().getTenantId();
        AiUseCase useCase = useCaseRepository.findEffective(tenantId, useCaseId)
                .orElseThrow(() -> new IllegalStateException("No policy registered for use case: " + useCaseId));

        if (!useCase.getEnabled()) {
            throw new IllegalStateException("Use case disabled: " + useCaseId);
        }

        // 1. Check kill switch
        List<AiKillSwitch> active = killSwitchRepository.findActiveForUseCase(tenantId, useCaseId);
        if (!active.isEmpty()) {
            throw new IllegalStateException("Use case is currently disabled: " +
                    active.get(0).getReason());
        }
        if (requestedProvider != null) {
            List<AiKillSwitch> providerKs = killSwitchRepository.findActiveForProvider(requestedProvider);
            if (!providerKs.isEmpty()) {
                throw new IllegalStateException("Provider is currently disabled: " + requestedProvider);
            }
        }

        // 2. Check provider allow list
        if (requestedProvider != null) {
            List<String> approved = Arrays.asList(useCase.getApprovedProviders().split("\\s*,\\s*"));
            if (!approved.contains(requestedProvider)) {
                throw new IllegalStateException("Provider not approved for this use case: " + requestedProvider);
            }
        }

        // 3. Check daily budget
        if (useCase.getDailyBudgetUsd() != null
                && useCase.getDailyBudgetUsd().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal today = tokenUsageService.todaySpend(tenantId);
            if (today != null && today.compareTo(useCase.getDailyBudgetUsd()) > 0) {
                throw new IllegalStateException("Daily AI budget exceeded for tenant: " + tenantId);
            }
        }

        return useCase;
    }

    // -------------------------------------------------------------------------
    // Kill switch
    // -------------------------------------------------------------------------

    @Transactional
    public AiKillSwitch activateKillSwitch(String scope, String useCaseId, String provider,
                                            String reason, String activatedBy, Instant expiresAt) {
        if (!Set.of("USE_CASE", "PROVIDER").contains(scope)) {
            throw new IllegalArgumentException("scope must be USE_CASE or PROVIDER");
        }
        if ("USE_CASE".equals(scope) && useCaseId == null) {
            throw new IllegalArgumentException("useCaseId required for USE_CASE scope");
        }
        if ("PROVIDER".equals(scope) && provider == null) {
            throw new IllegalArgumentException("provider required for PROVIDER scope");
        }
        AiKillSwitch ks = AiKillSwitch.builder()
                .killSwitchId(UUID.randomUUID().toString())
                .tenantId(TenantContext.get().getTenantId())
                .useCaseId("USE_CASE".equals(scope) ? useCaseId : null)
                .scope(scope)
                .provider("PROVIDER".equals(scope) ? provider : null)
                .active(true)
                .reason(reason)
                .activatedBy(activatedBy)
                .activatedAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
        log.warn("AI kill switch ACTIVATED: scope={}, useCaseId={}, provider={}, reason={}",
                scope, useCaseId, provider, reason);
        return killSwitchRepository.save(ks);
    }

    @Transactional
    public boolean deactivateKillSwitch(String killSwitchId) {
        return killSwitchRepository.findById(killSwitchId).map(ks -> {
            ks.setActive(false);
            killSwitchRepository.save(ks);
            log.info("AI kill switch deactivated: id={}", killSwitchId);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<AiKillSwitch> listActive() {
        String tenantId = TenantContext.get().getTenantId();
        return killSwitchRepository.findActive(tenantId, null, Instant.now());
    }

    // -------------------------------------------------------------------------
    // Prompt / template versioning
    // -------------------------------------------------------------------------

    /**
     * Create a new version of a prompt template. Auto-increments the
     * version number and computes the SHA-256 of the content for change
     * detection. Does NOT activate the new version — call {@link #activatePromptVersion}
     * after evaluation.
     */
    @Transactional
    public AiPromptVersion createPromptVersion(String templateId, String content,
                                               String industry, String changeNotes,
                                               String createdBy) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        String tenantId = TenantContext.get().getTenantId();
        Optional<AiPromptVersion> latest = promptVersionRepository
                .findFirstByTemplateIdAndIsActiveTrueOrderByVersionDesc(templateId);
        int nextVersion = latest.map(v -> v.getVersion() + 1).orElse(1);
        AiPromptVersion pv = AiPromptVersion.builder()
                .promptVersionId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .templateId(templateId)
                .version(nextVersion)
                .industry(industry)
                .contentHash(sha256Hex(content))
                .changeNotes(changeNotes)
                .isActive(false)
                .createdBy(createdBy)
                .build();
        AiPromptVersion saved = promptVersionRepository.save(pv);
        log.info("Prompt version created: templateId={}, version={}, hash={}",
                templateId, nextVersion, saved.getContentHash());
        return saved;
    }

    /**
     * Activate a specific prompt version. Deactivates any previously active
     * version for the same template. The cache is flushed via
     * {@link PromptTemplateService}.
     */
    @Transactional
    public AiPromptVersion activatePromptVersion(String templateId, Integer version) {
        Optional<AiPromptVersion> toActivate = promptVersionRepository
                .findByTemplateIdAndVersion(templateId, version);
        if (toActivate.isEmpty()) {
            throw new IllegalArgumentException("Version not found: " + templateId + " v" + version);
        }
        // Deactivate other versions
        List<AiPromptVersion> existing = promptVersionRepository
                .findByTemplateIdOrderByVersionDesc(templateId);
        for (AiPromptVersion v : existing) {
            if (Boolean.TRUE.equals(v.getIsActive())) {
                v.setIsActive(false);
                promptVersionRepository.save(v);
            }
        }
        AiPromptVersion pv = toActivate.get();
        pv.setIsActive(true);
        AiPromptVersion saved = promptVersionRepository.save(pv);
        log.info("Prompt version ACTIVATED: templateId={}, version={}", templateId, version);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AiPromptVersion> listPromptVersions(String templateId) {
        return promptVersionRepository.findByTemplateIdOrderByVersionDesc(templateId);
    }

    @Transactional(readOnly = true)
    public Optional<AiPromptVersion> getActivePromptVersion(String templateId) {
        return promptVersionRepository.findFirstByTemplateIdAndIsActiveTrueOrderByVersionDesc(templateId);
    }

    /**
     * Compare two prompt versions by content hash. Returns the diff signature
     * (hashes of each) so callers can see if a change is meaningful.
     */
    public PromptVersionDiff diffPromptVersions(String templateId, int v1, int v2) {
        AiPromptVersion p1 = promptVersionRepository.findByTemplateIdAndVersion(templateId, v1)
                .orElseThrow(() -> new IllegalArgumentException("v" + v1 + " not found"));
        AiPromptVersion p2 = promptVersionRepository.findByTemplateIdAndVersion(templateId, v2)
                .orElseThrow(() -> new IllegalArgumentException("v" + v2 + " not found"));
        return new PromptVersionDiff(
                p1.getVersion(), p1.getContentHash(), p1.getChangeNotes(),
                p2.getVersion(), p2.getContentHash(), p2.getChangeNotes(),
                !Objects.equals(p1.getContentHash(), p2.getContentHash()));
    }

    private static String sha256Hex(String content) {
        if (content == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public record PromptVersionDiff(
            int versionA,
            String hashA,
            String notesA,
            int versionB,
            String hashB,
            String notesB,
            boolean changed
    ) {}
}
