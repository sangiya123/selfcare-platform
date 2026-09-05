package com.omobio.usage.service;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.usage.adapter.BalanceProvider;
import com.omobio.usage.domain.UsageRecord;
import com.omobio.usage.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Persists usage history so the dashboard can show a 30-day history
 * and the AI recommendation engine has a queryable past.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageHistoryService {

    private final UsageRecordRepository usageRepository;

    @Transactional
    public UsageRecord record(String connectionId,
                              BalanceProvider.Balance balance,
                              BalanceProvider.UsageSummary summary,
                              String sourceProvider) {
        Instant now = Instant.now();
        Instant periodStart = summary != null && summary.getPeriodStart() != null
                ? summary.getPeriodStart()
                : now.minus(30, ChronoUnit.DAYS);
        Instant periodEnd = summary != null && summary.getPeriodEnd() != null
                ? summary.getPeriodEnd()
                : now;

        UsageRecord r = UsageRecord.builder()
                .tenantId(TenantContext.get().getTenantId())
                .connectionId(connectionId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .recordedAt(now)
                .balanceAmount(balance != null ? balance.getAmount() : null)
                .balanceCurrency(balance != null ? balance.getCurrency() : null)
                .balanceType(balance != null ? balance.getBalanceType() : null)
                .balanceExpiryAt(balance != null ? balance.getExpiryDate() : null)
                .balanceIsPrimary(balance != null ? balance.getIsPrimary() : null)
                .dataTotalBytes(summary != null && summary.getData() != null ? summary.getData().getTotalBytes() : null)
                .dataRemainingBytes(summary != null && summary.getData() != null ? summary.getData().getRemainingBytes() : null)
                .dataAllowanceBytes(summary != null && summary.getData() != null ? summary.getData().getAllowanceBytes() : null)
                .dataResetAt(summary != null && summary.getData() != null ? summary.getData().getResetDate() : null)
                .dataIsUnlimited(summary != null && summary.getData() != null ? summary.getData().getIsUnlimited() : null)
                .voiceTotalSeconds(summary != null && summary.getVoice() != null ? summary.getVoice().getTotalSeconds() : null)
                .voiceRemainingSeconds(summary != null && summary.getVoice() != null ? summary.getVoice().getRemainingSeconds() : null)
                .voiceAllowanceSeconds(summary != null && summary.getVoice() != null ? summary.getVoice().getAllowanceSeconds() : null)
                .voiceResetAt(summary != null && summary.getVoice() != null ? summary.getVoice().getResetDate() : null)
                .voiceIsUnlimited(summary != null && summary.getVoice() != null ? summary.getVoice().getIsUnlimited() : null)
                .smsTotalCount(summary != null && summary.getSms() != null ? summary.getSms().getTotalCount() : null)
                .smsRemainingCount(summary != null && summary.getSms() != null ? summary.getSms().getRemainingCount() : null)
                .smsAllowanceCount(summary != null && summary.getSms() != null ? summary.getSms().getAllowanceCount() : null)
                .smsResetAt(summary != null && summary.getSms() != null ? summary.getSms().getResetDate() : null)
                .smsIsUnlimited(summary != null && summary.getSms() != null ? summary.getSms().getIsUnlimited() : null)
                .sourceProvider(sourceProvider)
                .isStale(false)
                .build();
        return usageRepository.save(r);
    }

    @Transactional(readOnly = true)
    public List<UsageRecord> recentHistory(String connectionId, int days) {
        String tenantId = TenantContext.get().getTenantId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return usageRepository.findRecentForConnection(tenantId, connectionId, since);
    }

    @Transactional(readOnly = true)
    public UsageRecord lastKnown(String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return usageRepository.findFirstByTenantIdAndConnectionIdOrderByRecordedAtDesc(tenantId, connectionId)
                .orElse(null);
    }

    @Transactional
    public int pruneOlderThan(int days) {
        int deleted = usageRepository.deleteOlderThan(Instant.now().minus(days, ChronoUnit.DAYS));
        if (deleted > 0) {
            log.info("Pruned {} old usage records (older than {} days)", deleted, days);
        }
        return deleted;
    }
}
