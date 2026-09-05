package com.omobio.usage.service;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import com.omobio.usage.domain.Allowance;
import com.omobio.usage.repository.AllowanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Allowance management — tracks per-connection data/voice/SMS buckets
 * over time and exposes remaining/consumed views for the dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllowanceService {

    private final AllowanceRepository allowanceRepository;

    @Transactional(readOnly = true)
    public List<Allowance> getActiveAllowances(String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return allowanceRepository.findActiveAllowances(tenantId, connectionId);
    }

    @Transactional(readOnly = true)
    public Allowance getAllowance(String connectionId, String allowanceId) {
        String tenantId = TenantContext.get().getTenantId();
        return allowanceRepository
                .findByTenantIdAndConnectionIdAndAllowanceId(tenantId, connectionId, allowanceId)
                .orElseThrow(() -> new NotFoundException("Allowance", allowanceId));
    }

    /**
     * Create or update an allowance — typically called from the
     * Kafka listener when a recharge / package purchase event arrives.
     */
    @Transactional
    public Allowance upsertAllowance(Allowance allowance) {
        if (allowance.getTenantId() == null) {
            allowance.setTenantId(TenantContext.get().getTenantId());
        }
        if (allowance.getStatus() == null) {
            allowance.setStatus(determineStatus(allowance));
        }
        log.info("Upserting allowance: id={}, conn={}, remaining={}/{}",
                allowance.getAllowanceId(), allowance.getConnectionId(),
                allowance.getRemainingUnits(), allowance.getTotalUnits());
        return allowanceRepository.save(allowance);
    }

    /**
     * Record consumption (e.g. from real-time usage events).
     */
    @Transactional
    public void recordConsumption(String connectionId, String allowanceId, long unitsConsumed) {
        String tenantId = TenantContext.get().getTenantId();
        Allowance a = allowanceRepository
                .findByTenantIdAndConnectionIdAndAllowanceId(tenantId, connectionId, allowanceId)
                .orElseThrow(() -> new NotFoundException("Allowance", allowanceId));
        long used = (a.getUsedUnits() == null ? 0 : a.getUsedUnits()) + unitsConsumed;
        long remaining = Math.max(0, a.getTotalUnits() - used);
        String status = determineStatus(a);
        allowanceRepository.updateConsumption(tenantId, connectionId, allowanceId, used, remaining);
        a.setUsedUnits(used);
        a.setRemainingUnits(remaining);
        a.setStatus(status);
        log.debug("Allowance consumption: id={}, used={}, remaining={}, status={}",
                allowanceId, used, remaining, status);
    }

    /**
     * Mark allowances as expired when their expiry has passed.
     * Called by the daily rollup job.
     */
    @Transactional
    public int markExpired(Instant asOf) {
        int updated = allowanceRepository.markExpiredAsOf(asOf);
        if (updated > 0) {
            log.info("Marked {} allowances as expired (as of {})", updated, asOf);
        }
        return updated;
    }

    private String determineStatus(Allowance a) {
        if (a.getExpiresAt() != null && a.getExpiresAt().isBefore(Instant.now())) {
            return "EXPIRED";
        }
        if (a.getRemainingUnits() != null && a.getRemainingUnits() <= 0) {
            return "EXHAUSTED";
        }
        if (a.getStatus() != null && (a.getStatus().equals("CANCELLED"))) {
            return a.getStatus();
        }
        return "ACTIVE";
    }
}
