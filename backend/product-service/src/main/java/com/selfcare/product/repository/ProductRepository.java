package com.selfcare.product.repository;

import com.selfcare.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Product repository.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByTenantIdAndProductId(String tenantId, String productId);

    Optional<Product> findByTenantIdAndSourceSystemAndSourceProductId(
            String tenantId, String sourceSystem, String sourceProductId);

    List<Product> findByTenantIdAndStatusAndBadgeInOrderByDisplayOrderAsc(
            String tenantId, String status, List<String> badges, org.springframework.data.domain.Pageable pageable);

    List<Product> findByTenantIdAndStatusOrderByDisplayOrderAsc(String tenantId, String status);

    List<Product> findByTenantIdAndStatusAndLobOrderByDisplayOrderAsc(
            String tenantId, String status, String lob);

    @Query("SELECT p.category, COUNT(p) FROM Product p " +
           "WHERE p.tenantId = :tenantId AND p.status = 'ACTIVE' " +
           "GROUP BY p.category ORDER BY p.category")
    List<Object[]> countByCategoryForTenant(@Param("tenantId") String tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.status = 'ACTIVE' " +
           "AND p.id NOT IN (SELECT p2.productId FROM Product p2 " +
           "WHERE p2.tenantId = :tenantId AND p2.status = 'ACTIVE' " +
           "AND p2.updatedAt > :since)")
    List<Product> findStaleProducts(@Param("tenantId") String tenantId, @Param("since") java.time.Instant since);
}