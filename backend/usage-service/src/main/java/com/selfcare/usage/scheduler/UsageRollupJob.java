package com.selfcare.usage.scheduler;

import com.selfcare.usage.service.AllowanceService;
import com.selfcare.usage.service.UsageHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Daily maintenance jobs for the usage-service.
 *
 *  - {@link #expireAllowances()}: mark ACTIVE allowances whose expiresAt has passed as EXPIRED.
 *  - {@link #pruneOldHistory()}: delete usage_records older than the configured retention window.
 *
 * Both jobs are safe to run on a single instance (idempotent SQL updates).
 * For multi-instance deployments, use ShedLock (added separately) or a
 * single-replica deployment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageRollupJob {

    private final AllowanceService allowanceService;
    private final UsageHistoryService usageHistoryService;

    @Value("${usage.retention.days:90}")
    private int retentionDays;

    /** Daily at 02:15 server time */
    @Scheduled(cron = "${usage.rollup.allowances-cron:0 15 2 * * *}")
    public void expireAllowances() {
        try {
            int updated = allowanceService.markExpired(Instant.now());
            log.info("Daily allowance expiration complete: {} marked EXPIRED", updated);
        } catch (Exception e) {
            log.error("Allowance expiration job failed: {}", e.getMessage(), e);
        }
    }

    /** Daily at 03:00 server time */
    @Scheduled(cron = "${usage.rollup.prune-cron:0 0 3 * * *}")
    public void pruneOldHistory() {
        try {
            int deleted = usageHistoryService.pruneOlderThan(retentionDays);
            log.info("Daily usage history prune complete: {} records older than {} days deleted",
                    deleted, retentionDays);
        } catch (Exception e) {
            log.error("Prune job failed: {}", e.getMessage(), e);
        }
    }
}
