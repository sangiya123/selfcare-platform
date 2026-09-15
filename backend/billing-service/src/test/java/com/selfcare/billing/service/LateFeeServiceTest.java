package com.selfcare.billing.service;

import com.selfcare.billing.domain.Bill;
import com.selfcare.billing.domain.BillItem;
import com.selfcare.billing.domain.BillingCycleConfig;
import com.selfcare.billing.repository.BillItemRepository;
import com.selfcare.billing.repository.BillingCycleConfigRepository;
import com.selfcare.billing.repository.BillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LateFeeServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BillItemRepository billItemRepository;
    @Mock private BillingCycleConfigRepository configRepository;

    private LateFeeService service;

    @BeforeEach
    void setUp() {
        service = new LateFeeService(billRepository, billItemRepository, configRepository);
    }

    @Test
    @DisplayName("calculateLateFee returns zero for PAID bill")
    void zeroForPaidBill() {
        Bill bill = bill("b1", "PAID", LocalDate.now().minusDays(30), BigDecimal.valueOf(500));

        BigDecimal fee = service.calculateLateFee(bill);

        assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculateLateFee returns zero within grace period")
    void zeroWithinGracePeriod() {
        // Grace period is 3 days — due date is 2 days ago (within grace)
        Bill bill = bill("b1", "OVERDUE", LocalDate.now().minusDays(2), BigDecimal.valueOf(500));
        when(configRepository.findByTenantId("t1")).thenReturn(Optional.of(defaultConfig()));

        BigDecimal fee = service.calculateLateFee(bill);

        assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculateLateFee applies 2% to overdue bill after grace period")
    void appliesCorrectPercentage() {
        // Due date is 10 days ago — past grace period
        Bill bill = bill("b1", "OVERDUE", LocalDate.now().minusDays(10), BigDecimal.valueOf(500));
        when(configRepository.findByTenantId("t1")).thenReturn(Optional.of(defaultConfig()));

        BigDecimal fee = service.calculateLateFee(bill);

        // 2% of 500 = 10.00
        assertThat(fee).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    @DisplayName("calculateLateFee caps at lateFeeCap")
    void capsAtLateFeeCap() {
        Bill bill = bill("b1", "OVERDUE", LocalDate.now().minusDays(10), BigDecimal.valueOf(50_000));
        when(configRepository.findByTenantId("t1")).thenReturn(Optional.of(defaultConfig()));

        BigDecimal fee = service.calculateLateFee(bill);

        // 2% of 50000 = 1000, cap is 500
        assertThat(fee).isEqualByComparingTo(new BigDecimal("500.0000"));
    }

    @Test
    @DisplayName("applyLateFee skips if already applied")
    void skipsIfAlreadyApplied() {
        Bill bill = bill("b1", "OVERDUE", LocalDate.now().minusDays(10), BigDecimal.valueOf(500));
        BillItem existing = BillItem.builder()
                .billItemId("bi-latefee-b1")
                .billId("b1")
                .itemType("FEE")
                .category("LATE_FEE")
                .amount(new BigDecimal("10.0000"))
                .build();
        when(billRepository.findById("b1")).thenReturn(Optional.of(bill));
        when(billItemRepository.findByTenantIdAndBillIdOrderByCreatedAtAsc("t1", "b1"))
                .thenReturn(List.of(existing));

        BillItem result = service.applyLateFee("b1");

        assertThat(result).isEqualTo(existing);
        verify(billItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyLateFee creates new LATE_FEE bill item and updates bill total")
    void appliesLateFeeAndUpdatesBill() {
        Bill bill = bill("b1", "OVERDUE", LocalDate.now().minusDays(10), BigDecimal.valueOf(500));
        when(billRepository.findById("b1")).thenReturn(Optional.of(bill));
        when(configRepository.findByTenantId("t1")).thenReturn(Optional.of(defaultConfig()));
        when(billItemRepository.findByTenantIdAndBillIdOrderByCreatedAtAsc("t1", "b1"))
                .thenReturn(List.of());
        when(billItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillItem result = service.applyLateFee("b1");

        ArgumentCaptor<BillItem> itemCaptor = ArgumentCaptor.forClass(BillItem.class);
        verify(billItemRepository).save(itemCaptor.capture());
        BillItem saved = itemCaptor.getValue();
        assertThat(saved.getCategory()).isEqualTo("LATE_FEE");
        assertThat(saved.getItemType()).isEqualTo("FEE");
        // Bill total = 500 + 10 = 510
        verify(billRepository).save(argThat(b -> b.getTotalAmount().compareTo(new BigDecimal("510")) == 0));
    }

    private Bill bill(String id, String status, LocalDate dueDate, BigDecimal outstanding) {
        Bill b = new Bill();
        b.setBillId(id);
        b.setTenantId("t1");
        b.setConnectionId("conn-1");
        b.setStatus(status);
        b.setDueDate(dueDate);
        b.setTotalAmount(outstanding);
        b.setPaidAmount(BigDecimal.ZERO);
        b.setOutstandingAmount(outstanding);
        b.setCurrency("LKR");
        return b;
    }

    private BillingCycleConfig defaultConfig() {
        return BillingCycleConfig.builder()
                .tenantId("t1")
                .paymentDueDays(14)
                .lateFeePct(new BigDecimal("2.0"))
                .lateFeeCap(new BigDecimal("500.0"))
                .defaultTaxPct(new BigDecimal("18.0"))
                .gracePeriodDays(3)
                .currency("LKR")
                .build();
    }
}
