package com.selfcare.product.repository;

import com.selfcare.product.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfferRepository extends JpaRepository<Offer, String> {

    @Query("SELECT o FROM Offer o WHERE o.tenantId = :tenantId AND o.status = 'ACTIVE' " +
           "AND (o.validFrom IS NULL OR o.validFrom <= :now) " +
           "AND (o.validUntil IS NULL OR o.validUntil >= :now) " +
           "ORDER BY o.priority ASC, o.createdAt DESC")
    List<Offer> findActiveAsOf(
            @Param("tenantId") String tenantId,
            @Param("now") Instant now);

    List<Offer> findByTenantIdAndProductIdAndStatus(String tenantId, String productId, String status);

    Optional<Offer> findByTenantIdAndOfferId(String tenantId, String offerId);

    @Query("SELECT o FROM Offer o WHERE o.tenantId = :tenantId AND o.status = 'ACTIVE' " +
           "AND (o.targetSegment = :segment OR o.targetSegment = 'ALL') " +
           "AND (o.eligibleConnectionType = :connType OR o.eligibleConnectionType = 'ALL' OR o.eligibleConnectionType IS NULL) " +
           "ORDER BY o.priority ASC")
    List<Offer> findActiveForSegment(
            @Param("tenantId") String tenantId,
            @Param("segment") String segment,
            @Param("connType") String connType);

    @Query("SELECT o FROM Offer o WHERE o.tenantId = :tenantId AND o.productId = :productId " +
           "AND o.status = 'ACTIVE' " +
           "AND (o.validFrom IS NULL OR o.validFrom <= :now) " +
           "AND (o.validUntil IS NULL OR o.validUntil >= :now)")
    List<Offer> findActiveOffersForProduct(
            @Param("tenantId") String tenantId,
            @Param("productId") String productId,
            @Param("now") Instant now);
}
