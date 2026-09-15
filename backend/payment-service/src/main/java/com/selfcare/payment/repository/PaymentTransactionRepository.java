package com.selfcare.payment.repository;

import com.selfcare.payment.domain.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<PaymentTransaction> findByTenantIdAndTransactionId(String tenantId, String transactionId);

    Page<PaymentTransaction> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.tenantId = :tenantId AND p.status = 'UNKNOWN' " +
           "AND p.createdAt < :olderThan")
    List<PaymentTransaction> findStaleUnknowns(@Param("tenantId") String tenantId,
                                               @Param("olderThan") Instant olderThan);
}