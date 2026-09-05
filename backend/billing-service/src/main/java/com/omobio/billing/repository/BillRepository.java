package com.omobio.billing.repository;

import com.omobio.billing.domain.Bill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, String> {

    Optional<Bill> findByTenantIdAndBillId(String tenantId, String billId);

    @Query("SELECT b FROM Bill b WHERE b.tenantId = :tenantId " +
           "AND (:connectionId IS NULL OR b.connectionId = :connectionId) " +
           "AND (:status IS NULL OR b.status = :status) " +
           "AND (:fromDate IS NULL OR b.issueDate >= :fromDate) " +
           "AND (:toDate IS NULL OR b.issueDate <= :toDate)")
    Page<Bill> findWithFilters(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    Optional<Bill> findFirstByTenantIdAndConnectionIdAndStatusInOrderByDueDateAsc(
            String tenantId, String connectionId, List<String> statuses);

    @Query("SELECT b FROM Bill b WHERE b.tenantId = :tenantId AND b.status IN ('ISSUED', 'DUE', 'PARTIALLY_PAID', 'OVERDUE')")
    List<Bill> findAllPayable(@Param("tenantId") String tenantId);

    @Query("SELECT b.billId FROM Bill b WHERE b.status = 'OVERDUE' " +
           "AND b.outstandingAmount IS NOT NULL AND b.outstandingAmount > 0")
    List<String> findOverdueBillIds();

    @Query("SELECT b FROM Bill b WHERE b.status IN ('ISSUED', 'DUE') " +
           "AND b.dueDate IS NOT NULL AND b.dueDate < :today")
    List<Bill> findDueBillsPastDueDate(@Param("today") LocalDate today);

    /**
     * Promote ISSUED / DUE bills whose due date has passed to OVERDUE.
     * Returns the billIds that were updated.
     */
    default List<String> promoteOverdueBills(LocalDate today) {
        List<Bill> candidates = findDueBillsPastDueDate(today);
        for (Bill b : candidates) {
            b.setStatus("OVERDUE");
        }
        saveAll(candidates);
        return candidates.stream().map(Bill::getBillId).toList();
    }

    @Query("SELECT b.billId FROM Bill b WHERE b.tenantId = :tenantId " +
           "AND b.dueDate < :today AND b.status NOT IN ('PAID', 'CANCELLED')")
    List<String> findOverdueForTenant(
            @Param("tenantId") String tenantId,
            @Param("today") LocalDate today);
}
