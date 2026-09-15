package com.selfcare.billing.scheduler;

import com.selfcare.billing.repository.BillRepository;
import com.selfcare.billing.service.LateFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled billing jobs.
 *
 *  - Daily late fee check: at 01:00 each day, find all OVERDUE bills
 *    and apply late fees per the tenant's BillingCycleConfig.
 *  - Daily overdue promotion: at 00:30, move DUE bills past their due date
 *    to OVERDUE status.
 *
 * Both jobs use idempotent logic — safe to run on any instance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final BillRepository billRepository;
    private final LateFeeService lateFeeService;

    /** Daily at 00:30 — promote DUE → OVERDUE */
    @Scheduled(cron = "${billing.overdue-promotion-cron:0 30 0 * * *}")
    public void promoteOverdueBills() {
        try {
            List<String> updated = billRepository.promoteOverdueBills(LocalDate.now());
            if (!updated.isEmpty()) {
                log.info("Promoted {} bills from DUE to OVERDUE", updated.size());
            }
        } catch (Exception e) {
            log.error("Overdue promotion job failed: {}", e.getMessage(), e);
        }
    }

    /** Daily at 01:00 — apply late fees to overdue bills */
    @Scheduled(cron = "${billing.late-fee-cron:0 0 1 * * *}")
    public void applyLateFees() {
        try {
            List<String> overdueBillIds = billRepository.findOverdueBillIds();
            int applied = 0;
            for (String billId : overdueBillIds) {
                try {
                    if (lateFeeService.applyLateFee(billId) != null) {
                        applied++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to apply late fee to bill {}: {}", billId, e.getMessage());
                }
            }
            log.info("Late fee job complete: applied {} fees out of {} overdue bills",
                    applied, overdueBillIds.size());
        } catch (Exception e) {
            log.error("Late fee job failed: {}", e.getMessage(), e);
        }
    }
}
