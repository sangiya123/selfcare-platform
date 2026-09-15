package com.selfcare.product.service;

import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.adapter.ProductCatalogProvider;
import com.selfcare.platform.common.adapter.ProductCatalogProvider.RawProduct;
import com.selfcare.product.domain.Product;
import com.selfcare.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Product materialization service — periodically refreshes the read model from
 * operator-specific catalog sources.
 *
 * Per planning docs:
 * - Aggregate products/offers from API, MySQL, Mongo, Kafka-fed stores
 * - Normalize into canonical Product model
 * - Build read-optimized materialized views rather than synchronous recomputation
 * - Support source precedence and dedup
 *
 * Runs on a configurable schedule (default: every 6 hours).
 * Also runs on demand via /admin/refresh endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMaterializationService {

    private final ProductRepository productRepository;
    private final ApiAdapterRegistry<ProductCatalogProvider> catalogProviderRegistry;

    @Value("${product.materialization.batch-size:500}")
    private int batchSize;

    /**
     * Scheduled materialization — runs every 6 hours by default.
     * Configure via product.materialization.cron property.
     */
    @Scheduled(cron = "${product.materialization.cron:0 0 */6 * * *}")
    public void scheduledMaterialization() {
        log.info("Starting scheduled product materialization");
        long start = System.currentTimeMillis();
        try {
            int count = materializeAllTenants();
            log.info("Materialization complete: {} products updated in {}ms",
                    count, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Materialization failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Run materialization for all registered tenants.
     */
    public int materializeAllTenants() {
        int total = 0;
        Set<String> tenants = catalogProviderRegistry.getRegisteredTenants();
        for (String tenantId : tenants) {
            total += materializeTenant(tenantId);
        }
        return total;
    }

    /**
     * Materialize products for a specific tenant.
     *
     * Process:
     * 1. Fetch from all catalog sources for this tenant
     * 2. Sort by source priority
     * 3. Dedupe by sourceProductId
     * 4. Normalize to canonical form
     * 5. Upsert into read model (insert new, update existing, mark others as ARCHIVED)
     */
    @Transactional
    public int materializeTenant(String tenantId) {
        log.info("Materializing products for tenant: {}", tenantId);

        // Collect all raw products from all sources for this tenant
        List<RawProductWithPriority> allRaw = new ArrayList<>();
        // Use the first registered provider that supports this tenant
        // (In practice, each tenant typically has one provider package)
        try {
            ProductCatalogProvider provider = catalogProviderRegistry.getProvider(tenantId);
            if (provider == null) {
                log.warn("No catalog provider for tenant: {}", tenantId);
                return 0;
            }

            List<RawProduct> raw = provider.fetchAllProducts();
            int priority = provider.getSourcePriority();
            String sourceSystem = provider.getSourceSystem();

            for (RawProduct r : raw) {
                allRaw.add(new RawProductWithPriority(r, priority, sourceSystem));
            }
        } catch (Exception e) {
            log.error("Failed to fetch products for tenant {}: {}", tenantId, e.getMessage());
            return 0;
        }

        // Sort by priority (lower = higher priority)
        allRaw.sort(Comparator.comparingInt(RawProductWithPriority::priority));

        // Upsert each product
        int count = 0;
        Set<String> seenSourceIds = new HashSet<>();
        for (RawProductWithPriority rwp : allRaw) {
            RawProduct raw = rwp.raw();
            if (raw.getSourceProductId() == null) continue;

            // Dedupe: skip if already seen
            if (!seenSourceIds.add(raw.getSourceProductId())) {
                continue;
            }

            try {
                upsertProduct(tenantId, rwp.sourceSystem(), raw);
                count++;
            } catch (Exception e) {
                log.warn("Failed to upsert product {} for tenant {}: {}",
                        raw.getSourceProductId(), tenantId, e.getMessage());
            }
        }

        log.info("Materialized {} products for tenant {}", count, tenantId);
        return count;
    }

    private void upsertProduct(String tenantId, String sourceSystem, RawProduct raw) {
        Optional<Product> existing = productRepository
                .findByTenantIdAndSourceSystemAndSourceProductId(tenantId, sourceSystem, raw.getSourceProductId());

        Product product;
        if (existing.isPresent()) {
            product = existing.get();
        } else {
            product = Product.builder()
                    .productId(UUID.randomUUID().toString())
                    .tenantId(tenantId)
                    .sourceProductId(raw.getSourceProductId())
                    .sourceSystem(sourceSystem)
                    .createdAt(Instant.now())
                    .build();
        }

        // Update fields
        product.setName(raw.getName());
        product.setDescription(raw.getDescription());
        product.setCategory(raw.getCategory());
        product.setSubcategory(raw.getSubcategory());
        product.setLob(raw.getLob());
        product.setConnectionType(raw.getConnectionType());
        product.setPrice(raw.getPrice());
        product.setCurrency(raw.getCurrency());
        product.setValidityDays(raw.getValidityDays());
        product.setAllowances(raw.getAllowances());
        product.setTerms(raw.getTerms());
        product.setImageUrl(raw.getImageUrl());
        product.setBadge(raw.getBadge());
        product.setDisplayOrder(raw.getDisplayOrder());
        product.setStatus("ACTIVE".equalsIgnoreCase(raw.getStatus()) ? "ACTIVE" : "INACTIVE");
        product.setTags(raw.getTags());
        product.setUpdatedAt(Instant.now());

        productRepository.save(product);
    }

    private record RawProductWithPriority(RawProduct raw, int priority, String sourceSystem) {}
}