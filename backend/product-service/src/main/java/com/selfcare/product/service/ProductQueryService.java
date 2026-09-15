package com.selfcare.product.service;

import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.dto.PaginationResponse;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.product.domain.Product;
import com.selfcare.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.*;

/**
 * Product query service — read-side operations for the catalog.
 *
 * Implements:
 * - Pagination + filtering
 * - Free-text search
 * - Category aggregation
 * - Featured product selection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public PaginationResponse<Product> listProducts(
            String tenantId, String category, String lob, String connectionType,
            String badge, String search, PaginationRequest pagination) {

        Specification<Product> spec = buildSpec(tenantId, category, lob, connectionType, badge, search);

        PageRequest pageRequest = PageRequest.of(
                pagination.getPage(),
                pagination.getSize(),
                Sort.by(Sort.Direction.fromString(pagination.getSortDirection()),
                        pagination.getSortBy() != null ? pagination.getSortBy() : "displayOrder"));

        Page<Product> page = productRepository.findAll(spec, pageRequest);

        return PaginationResponse.<Product>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public Product getProduct(String tenantId, String productId) {
        return productRepository.findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getCategoryCounts(String tenantId) {
        List<Object[]> results = productRepository.countByCategoryForTenant(tenantId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : results) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Product> getFeatured(String tenantId, int limit) {
        return productRepository.findByTenantIdAndStatusAndBadgeInOrderByDisplayOrderAsc(
                tenantId, "ACTIVE",
                List.of("POPULAR", "RECOMMENDED", "NEW", "LIMITED_TIME"),
                PageRequest.of(0, limit));
    }

    /**
     * List all active products for a tenant, optionally filtered by line of business.
     * Used by package browsing (data/voice/combo/roaming/DTV flows).
     */
    @Transactional(readOnly = true)
    public List<Product> listActiveProducts(String tenantId, String lob) {
        if (lob != null && !lob.isBlank()) {
            return productRepository.findByTenantIdAndStatusAndLobOrderByDisplayOrderAsc(
                    tenantId, "ACTIVE", lob.trim().toUpperCase());
        }
        return productRepository.findByTenantIdAndStatusOrderByDisplayOrderAsc(tenantId, "ACTIVE");
    }

    private Specification<Product> buildSpec(
            String tenantId, String category, String lob, String connectionType,
            String badge, String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (lob != null && !lob.isBlank()) {
                predicates.add(cb.equal(root.get("lob"), lob));
            }
            if (connectionType != null && !connectionType.isBlank()) {
                predicates.add(cb.or(
                        cb.equal(root.get("connectionType"), connectionType),
                        cb.equal(root.get("connectionType"), "both")
                ));
            }
            if (badge != null && !badge.isBlank()) {
                predicates.add(cb.equal(root.get("badge"), badge));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}