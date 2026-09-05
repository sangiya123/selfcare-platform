package com.omobio.ai.scheduler;

import com.omobio.ai.domain.AiEvaluationResult;
import com.omobio.ai.domain.AiKillSwitch;
import com.omobio.ai.domain.AiUseCase;
import com.omobio.ai.domain.ChatSession;
import com.omobio.ai.repository.AiEvaluationResultRepository;
import com.omobio.ai.repository.AiKillSwitchRepository;
import com.omobio.ai.repository.AiUseCaseRepository;
import com.omobio.ai.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Retention enforcement for AI data, per the AI governance spec
 * "retention policy for prompts/responses".
 *
 * Each use case declares a {@code retention_days} value. This job:
 *  - Prunes chat sessions older than their per-use-case retention window
 *  - Prunes evaluation results older than their per-use-case retention
 *  - Deactivates expired kill switches
 *
 * Default per-use-case retention:
 *  - customer_chat: 30 days
 *  - support_summary: 30 days
 *  - recommendations: 30 days
 *  - content_rewrite: 30 days
 *  - payment_action_prep: 90 days (HIGH risk)
 *  - claim_decision_support: 90 days (HIGH risk)
 *
 * Runs daily at 03:00 server time. Per-tenant extension is honored
 * automatically because retention values come from {@link AiUseCase#getRetentionDays()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRetentionJob {

    private final ChatSessionRepository chatSessionRepository;
    private final AiEvaluationResultRepository evaluationResultRepository;
    private final AiKillSwitchRepository killSwitchRepository;
    private final AiUseCaseRepository useCaseRepository;

    /** Platform default retention when a use case has no explicit policy. */
    @Value("${ai.retention.default-days:30}")
    private int defaultRetentionDays;

    @Value("${ai.retention.physical-delete-enabled:false}")
    private boolean physicalDeleteEnabled;

    /**
     * Daily retention sweep.
     */
    @Scheduled(cron = "${ai.retention.cron:0 0 3 * * *}")
    public void enforceRetention() {
        log.info("AI retention sweep starting (physicalDeleteEnabled={}, defaultRetentionDays={})",
                physicalDeleteEnabled, defaultRetentionDays);
        long start = System.currentTimeMillis();

        Map<String, Integer> retentionByUseCase = lookupRetentionDaysByUseCase();

        int sessionsPruned = pruneChatSessions(retentionByUseCase);
        int evaluationsPruned = pruneEvaluationResults(retentionByUseCase);
        int killSwitchesPruned = pruneExpiredKillSwitches();

        long elapsed = System.currentTimeMillis() - start;
        log.info("AI retention sweep complete in {}ms: sessions={}, evaluations={}, killSwitches={}",
                elapsed, sessionsPruned, evaluationsPruned, killSwitchesPruned);
    }

    /**
     * Resolve the retention_days for each registered use case.
     */
    private Map<String, Integer> lookupRetentionDaysByUseCase() {
        return useCaseRepository.findAll().stream()
                .filter(uc -> uc.getRetentionDays() != null)
                .collect(Collectors.toMap(
                        AiUseCase::getUseCaseId,
                        AiUseCase::getRetentionDays,
                        (a, b) -> a));
    }

    /**
     * Prune chat sessions older than their use case's retention window.
     * The ChatSession entity already has an {@code expiresAt} field — this
     * job respects it AND additionally enforces the per-use-case retention.
     */
    private int pruneChatSessions(Map<String, Integer> retentionByUseCase) {
        List<ChatSession> expired = chatSessionRepository.findExpiredSessions(Instant.now());
        if (expired.isEmpty()) return 0;
        int n = 0;
        for (ChatSession s : expired) {
            // Use the use case's retention if known, otherwise platform default
            int retention = retentionByUseCase.getOrDefault(s.getUseCaseId(), defaultRetentionDays);
            Instant cutoff = Instant.now().minus(retention, ChronoUnit.DAYS);
            if (s.getStartedAt() != null && s.getStartedAt().isBefore(cutoff)) {
                if (physicalDeleteEnabled) {
                    chatSessionRepository.delete(s);
                } else {
                    s.setActive(false);
                    chatSessionRepository.save(s);
                }
                n++;
            }
        }
        if (n > 0) {
            log.info("Chat sessions retention: {} sessions marked inactive/expired", n);
        }
        return n;
    }

    /**
     * Prune AI evaluation results older than their use case's retention window.
     */
    private int pruneEvaluationResults(Map<String, Integer> retentionByUseCase) {
        int totalPruned = 0;
        for (Map.Entry<String, Integer> entry : retentionByUseCase.entrySet()) {
            String useCaseId = entry.getKey();
            int retention = entry.getValue();
            Instant cutoff = Instant.now().minus(retention, ChronoUnit.DAYS);
            List<AiEvaluationResult> old = evaluationResultRepository
                    .findByUseCaseIdAndRunAtBefore(useCaseId, cutoff);
            if (!old.isEmpty()) {
                if (physicalDeleteEnabled) {
                    evaluationResultRepository.deleteAll(old);
                }
                totalPruned += old.size();
            }
        }
        if (totalPruned > 0) {
            log.info("AI evaluation results retention: {} records pruned", totalPruned);
        }
        return totalPruned;
    }

    /**
     * Deactivate kill switches that have passed their expires_at time.
     */
    private int pruneExpiredKillSwitches() {
        int n = 0;
        List<AiKillSwitch> active = killSwitchRepository.findAll();
        for (AiKillSwitch ks : active) {
            if (Boolean.TRUE.equals(ks.getActive())
                    && ks.getExpiresAt() != null
                    && ks.getExpiresAt().isBefore(Instant.now())) {
                ks.setActive(false);
                killSwitchRepository.save(ks);
                n++;
            }
        }
        if (n > 0) {
            log.info("AI kill switch retention: {} expired switches deactivated", n);
        }
        return n;
    }
}
