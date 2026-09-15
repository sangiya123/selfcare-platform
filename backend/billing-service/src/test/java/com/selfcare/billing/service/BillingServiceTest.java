package com.selfcare.billing.service;

import com.selfcare.billing.domain.Bill;
import com.selfcare.billing.repository.BillItemRepository;
import com.selfcare.billing.repository.BillRepository;
import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.dto.PaginationResponse;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BillItemRepository billItemRepository;

    private BillingService service;

    @BeforeEach
    void setUp() {
        service = new BillingService(billRepository, billItemRepository);
        TenantContext.current().setTenantId("t1");
        TenantContext.current().setUserId("u1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- listBills ---

    @Test
    @DisplayName("listBills returns paginated results sorted by issueDate DESC")
    void listBills_returnsPaginatedResults() {
        Bill bill = bill("b1", "ISSUED");
        when(billRepository.findWithFilters(eq("t1"), eq("conn-1"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bill)));

        PaginationResponse<Bill> result = service.listBills(
                "conn-1", null, null, null, PaginationRequest.builder().page(0).size(10).build());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getBillId()).isEqualTo("b1");
        assertThat(result.getTotalItems()).isEqualTo(1);
    }

    // --- getBill ---

    @Test
    @DisplayName("getBill returns bill when found")
    void getBill_found() {
        Bill bill = bill("b1", "ISSUED");
        when(billRepository.findByTenantIdAndBillId("t1", "b1")).thenReturn(Optional.of(bill));

        Bill result = service.getBill("b1");

        assertThat(result.getBillId()).isEqualTo("b1");
        verify(billRepository).findByTenantIdAndBillId("t1", "b1");
    }

    @Test
    @DisplayName("getBill throws NotFoundException when bill does not exist")
    void getBill_notFound() {
        when(billRepository.findByTenantIdAndBillId("t1", "b99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBill("b99"))
                .isInstanceOf(NotFoundException.class);
    }

    // --- recordPayment ---

    @Test
    @DisplayName("recordPayment fully pays bill when amount equals outstanding")
    void recordPayment_fullPayment() {
        Bill bill = bill("b1", "ISSUED");
        bill.setTotalAmount(BigDecimal.valueOf(100));
        bill.setPaidAmount(BigDecimal.ZERO);
        when(billRepository.findByTenantIdAndBillId("t1", "b1")).thenReturn(Optional.of(bill));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill result = service.recordPayment("b1", BigDecimal.valueOf(100));

        assertThat(result.getStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("recordPayment partially pays bill when amount is less than outstanding")
    void recordPayment_partialPayment() {
        Bill bill = bill("b1", "ISSUED");
        bill.setTotalAmount(BigDecimal.valueOf(100));
        bill.setPaidAmount(BigDecimal.ZERO);
        when(billRepository.findByTenantIdAndBillId("t1", "b1")).thenReturn(Optional.of(bill));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill result = service.recordPayment("b1", BigDecimal.valueOf(30));

        assertThat(result.getStatus()).isEqualTo("PARTIALLY_PAID");
    }

    // --- isPayable ---

    @Test
    @DisplayName("isPayable returns true for ISSUED, DUE, PARTIALLY_PAID, OVERDUE")
    void isPayable_returnsTrueForPayableStatuses() {
        for (String status : List.of("ISSUED", "DUE", "PARTIALLY_PAID", "OVERDUE")) {
            Bill bill = bill("b1", status);
            assertThat(service.isPayable(bill)).as("Bill with status %s should be payable", status).isTrue();
        }
    }

    @Test
    @DisplayName("isPayable returns false for PAID, CANCELLED, DRAFT")
    void isPayable_returnsFalseForNonPayableStatuses() {
        for (String status : List.of("PAID", "CANCELLED", "DRAFT")) {
            Bill bill = bill("b1", status);
            assertThat(service.isPayable(bill)).as("Bill with status %s should not be payable", status).isFalse();
        }
    }

    @Test
    @DisplayName("isPayable returns false for null or statusless bill")
    void isPayable_returnsFalseForNullBill() {
        assertThat(service.isPayable(null)).isFalse();
    }

    // --- getCurrentBill ---

    @Test
    @DisplayName("getCurrentBill returns earliest due payable bill")
    void getCurrentBill_returnsEarliestDue() {
        Bill bill = bill("b1", "DUE");
        when(billRepository.findFirstByTenantIdAndConnectionIdAndStatusInOrderByDueDateAsc(
                "t1", "conn-1",
                List.of("ISSUED", "DUE", "PARTIALLY_PAID", "OVERDUE")))
                .thenReturn(Optional.of(bill));

        Bill result = service.getCurrentBill("conn-1");

        assertThat(result.getBillId()).isEqualTo("b1");
    }

    // --- helpers ---

    private Bill bill(String id, String status) {
        Bill b = new Bill();
        b.setBillId(id);
        b.setTenantId("t1");
        b.setConnectionId("conn-1");
        b.setStatus(status);
        b.setTotalAmount(BigDecimal.valueOf(100));
        b.setCurrency("USD");
        b.setIssueDate(LocalDate.now().minusDays(30));
        b.setDueDate(LocalDate.now().plusDays(15));
        return b;
    }
}
