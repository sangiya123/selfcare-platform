package com.selfcare.billing.service;

import com.selfcare.billing.domain.Bill;
import com.selfcare.billing.domain.BillItem;
import com.selfcare.billing.domain.BillingCycleConfig;
import com.selfcare.billing.repository.BillItemRepository;
import com.selfcare.billing.repository.BillingCycleConfigRepository;
import com.selfcare.billing.repository.BillRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Late fee calculation and application.
 *
 * Rules (configured per tenant via BillingCycleConfig):
 *  1. Apply late fee only after gracePeriodDays past due date
 *  2. Fee = outstanding × lateFeePct (e.g. 2% of amount due)
 *  3. Cap at lateFeeCap if configured
 *  4. Applied as a new BILL_ITEM row with category = LATE_FEE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LateFeeService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillingCycleConfigRepository configRepository;

    @Transactional(readOnly = true)
    public BigDecimal calculateLateFee(String billId) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return BigDecimal.ZERO;
        return calculateLateFee(bill);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateLateFee(Bill bill) {
        if (!"OVERDUE".equals(bill.getStatus()) && !"DUE".equals(bill.getStatus())) {
            return BigDecimal.ZERO;
        }
        String tenantId = bill.getTenantId() != null ? bill.getTenantId() : TenantContext.get().getTenantId();
        BillingCycleConfig cfg = configRepository.findByTenantId(tenantId).orElse(null);
        if (cfg == null) return BigDecimal.ZERO;

        LocalDate dueDate = bill.getDueDate();
        if (dueDate == null) return BigDecimal.ZERO;

        LocalDate graceEnd = dueDate.plusDays(cfg.getGracePeriodDays() != null ? cfg.getGracePeriodDays() : 0);
        if (LocalDate.now().isBefore(graceEnd)) return BigDecimal.ZERO;

        BigDecimal outstanding = bill.getOutstandingAmount();
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal pct = cfg.getLateFeePct() != null ? cfg.getLateFeePct() : new BigDecimal("2.0");
        BigDecimal fee = outstanding
                .multiply(pct)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        if (cfg.getLateFeeCap() != null && fee.compareTo(cfg.getLateFeeCap()) > 0) {
            fee = cfg.getLateFeeCap();
        }
        return fee.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Apply late fee to a bill — creates a BILL_ITEM row.
     * Idempotent: skips if a LATE_FEE item already exists for this bill.
     */
    @Transactional
    public BillItem applyLateFee(String billId) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return null;

        String tenantId = bill.getTenantId() != null ? bill.getTenantId() : TenantContext.get().getTenantId();

        // Check if already applied
        List<BillItem> existing = billItemRepository
                .findByTenantIdAndBillIdOrderByCreatedAtAsc(tenantId, billId);
        boolean alreadyApplied = existing.stream()
                .anyMatch(i -> "FEE".equals(i.getItemType()) && "LATE_FEE".equals(i.getCategory()));
        if (alreadyApplied) {
            log.info("Late fee already applied to bill {}", billId);
            return existing.stream()
                    .filter(i -> "FEE".equals(i.getItemType()) && "LATE_FEE".equals(i.getCategory()))
                    .findFirst().orElse(null);
        }

        BigDecimal fee = calculateLateFee(bill);
        if (fee.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No late fee to apply for bill {}", billId);
            return null;
        }

        BillItem item = BillItem.builder()
                .billItemId("bi-latefee-" + billId)
                .tenantId(tenantId)
                .billId(billId)
                .connectionId(bill.getConnectionId())
                .itemType("FEE")
                .category("LATE_FEE")
                .description("Late payment fee")
                .amount(fee)
                .build();

        billItemRepository.save(item);

        // Update bill total
        bill.setTotalAmount(bill.getTotalAmount().add(fee));
        bill.calculateOutstanding();
        billRepository.save(bill);

        log.info("Applied late fee {} to bill {}", fee, billId);
        return item;
    }
}
