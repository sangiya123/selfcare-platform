package com.selfcare.ai.scheduler;

import com.selfcare.ai.service.PredictiveMLService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic batch job that prunes expired prediction results.
 *
 * The precompute itself is driven by upstream Kafka-fed read models
 * (e.g. account.changed, payment.events) calling
 * {@link PredictiveMLService#precompute(String, String, String, String, PredictiveMLService.Features)}.
 *
 * This job is a safety net to prevent the read model from growing unboundedly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionBatchJob {

    private final PredictiveMLService predictiveMLService;

    /** Daily at 03:30 server time */
    @Scheduled(cron = "${ai.prediction.prune-cron:0 30 3 * * *}")
    public void pruneExpiredPredictions() {
        try {
            int deleted = predictiveMLService.pruneExpired();
            if (deleted > 0) {
                log.info("Prediction read model pruned: {} expired entries removed", deleted);
            }
        } catch (Exception e) {
            log.error("Prediction prune job failed: {}", e.getMessage(), e);
        }
    }
}
