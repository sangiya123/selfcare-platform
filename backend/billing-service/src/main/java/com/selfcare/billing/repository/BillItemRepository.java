package com.selfcare.billing.repository;

import com.selfcare.billing.domain.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BillItemRepository extends JpaRepository<BillItem, String> {

    List<BillItem> findByTenantIdAndBillIdOrderByCreatedAtAsc(String tenantId, String billId);

    @Query("SELECT i FROM BillItem i WHERE i.tenantId = :tenantId " +
           "AND i.connectionId = :connectionId AND i.periodStart >= :since " +
           "ORDER BY i.createdAt DESC")
    List<BillItem> findRecentForConnection(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("since") Instant since);

    @Query("SELECT i.category, SUM(i.amount) FROM BillItem i " +
           "WHERE i.tenantId = :tenantId AND i.connectionId = :connectionId " +
           "AND i.periodStart >= :since AND i.itemType = 'CHARGE' " +
           "GROUP BY i.category ORDER BY SUM(i.amount) DESC")
    List<Object[]> sumByCategory(
            @Param("tenantId") String tenantId,
            @Param("connectionId") String connectionId,
            @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM BillItem i WHERE i.tenantId = :tenantId AND i.billId = :billId")
    int deleteByBill(@Param("tenantId") String tenantId, @Param("billId") String billId);
}
