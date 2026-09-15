package com.selfcare.billing.service;

import com.selfcare.billing.domain.Bill;
import com.selfcare.billing.domain.BillItem;
import com.selfcare.billing.repository.BillItemRepository;
import com.selfcare.billing.repository.BillRepository;
import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.dto.PaginationResponse;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Billing query and management service.
 *
 * Provides:
 * - Bill listing with pagination and filters
 * - Bill detail
 * - Bill status tracking
 * - Cross-connection bill payment authorization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;

    /**
     * List bills for a connection.
     */
    @Transactional(readOnly = true)
    public PaginationResponse<Bill> listBills(
            String connectionId, String status, LocalDate fromDate, LocalDate toDate,
            PaginationRequest pagination) {

        String tenantId = TenantContext.get().getTenantId();

        Page<Bill> page = billRepository.findWithFilters(
                tenantId, connectionId, status, fromDate, toDate,
                PageRequest.of(
                        pagination.getPage(),
                        pagination.getSize(),
                        Sort.by(Sort.Direction.DESC, "issueDate")));

        return PaginationResponse.<Bill>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * Get bill detail.
     */
    @Transactional(readOnly = true)
    public Bill getBill(String billId) {
        String tenantId = TenantContext.get().getTenantId();
        Bill bill = billRepository.findByTenantIdAndBillId(tenantId, billId)
                .orElseThrow(() -> new NotFoundException("Bill", billId));

        // Calculate outstanding
        bill.calculateOutstanding();
        return bill;
    }

    /**
     * Get current outstanding bill for a connection.
     */
    @Transactional(readOnly = true)
    public Bill getCurrentBill(String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return billRepository.findFirstByTenantIdAndConnectionIdAndStatusInOrderByDueDateAsc(
                tenantId, connectionId,
                List.of("ISSUED", "DUE", "PARTIALLY_PAID", "OVERDUE"))
                .map(bill -> {
                    bill.calculateOutstanding();
                    return bill;
                })
                .orElse(null);
    }

    /**
     * Mark bill as paid (called by payment service after successful payment).
     * Uses optimistic locking for concurrency safety.
     */
    @Transactional
    public Bill recordPayment(String billId, BigDecimal amount) {
        String tenantId = TenantContext.get().getTenantId();
        Bill bill = billRepository.findByTenantIdAndBillId(tenantId, billId)
                .orElseThrow(() -> new NotFoundException("Bill", billId));

        BigDecimal currentPaid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(amount);
        bill.setPaidAmount(newPaid);
        bill.calculateOutstanding();

        if (bill.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus("PAID");
        } else {
            bill.setStatus("PARTIALLY_PAID");
        }

        log.info("Payment recorded: bill={}, amount={}, newStatus={}",
                billId, amount, bill.getStatus());
        return billRepository.save(bill);
    }

    /**
     * Check if a bill can be paid (status, due date, business rules).
     */
    public boolean isPayable(Bill bill) {
        if (bill == null) return false;
        if (bill.getStatus() == null) return false;
        return switch (bill.getStatus()) {
            case "ISSUED", "DUE", "PARTIALLY_PAID", "OVERDUE" -> true;
            default -> false;
        };
    }

    /**
     * List line items for a bill.
     */
    @Transactional(readOnly = true)
    public List<BillItem> getBillItems(String billId) {
        String tenantId = TenantContext.get().getTenantId();
        // Ensure bill exists for the tenant
        billRepository.findByTenantIdAndBillId(tenantId, billId)
                .orElseThrow(() -> new NotFoundException("Bill", billId));
        return billItemRepository.findByTenantIdAndBillIdOrderByCreatedAtAsc(tenantId, billId);
    }

    /**
     * Return per-category spending for the last N days for a connection.
     * Used by the AI recommendation engine and analytics.
     */
    @Transactional(readOnly = true)
    public List<Object[]> categorySpending(String connectionId, int days) {
        String tenantId = TenantContext.get().getTenantId();
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofDays(days));
        return billItemRepository.sumByCategory(tenantId, connectionId, since);
    }
}