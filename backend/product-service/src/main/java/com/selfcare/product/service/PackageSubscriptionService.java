package com.selfcare.product.service;

import com.selfcare.platform.common.adapter.ActivationProvider;
import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.EligibilityProvider;
import com.selfcare.platform.common.web.NotFoundException;
import com.selfcare.product.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Package subscription service — canonical package browsing, eligibility,
 * activation and deactivation.
 *
 * <p>Combines the materialized catalog read-model with operator-provided
 * eligibility and activation capabilities. The operator-specific logic
 * (BSS calls) is delegated to the {@link ActivationProvider} /
 * {@link EligibilityProvider} beans registered for the tenant, so the core
 * platform stays industry-neutral.</p>
 *
 * <p>Data sources never change: the catalog read-model is materialized from
 * the operator's own catalog and eligibility/activation go straight to the
 * operator BSS via the provider pack.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageSubscriptionService {

    private final ProductQueryService queryService;
    private final List<ActivationProvider> activationProviders;
    private final List<EligibilityProvider> eligibilityProviders;

    /**
     * Browse the package catalog for a tenant, optionally enriched with the
     * caller's eligibility and current subscription state.
     *
     * @param tenantId     the tenant identifier
     * @param category     optional catalog category filter
     * @param lob          optional line-of-business filter (DATA, VOICE, COMBO, ROAMING, DTV, ...)
     * @param connectionId optional connection ID — when present, eligibility and
     *                     active-state flags are resolved from the operator
     * @return package offers
     */
    @Transactional(readOnly = true)
    public List<PackageOffer> browsePackages(String tenantId, String category, String lob,
                                             String connectionId) {
        List<Product> products = queryService.listActiveProducts(tenantId, lob);

        Map<String, EligibilityProvider.EligibleProduct> eligibleByCode =
                resolveEligible(tenantId, connectionId);
        Map<String, ActivationProvider.ActivePackage> activeByCode =
                resolveActive(tenantId, connectionId);

        return products.stream()
                .filter(p -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(p.getCategory())
                        || category.equalsIgnoreCase(p.getLob()))
                .map(p -> toOffer(p, eligibleByCode.get(p.getSourceProductId()),
                        activeByCode.get(p.getSourceProductId())))
                .toList();
    }

    /**
     * Get details for a single package, optionally with eligibility for a connection.
     */
    @Transactional(readOnly = true)
    public PackageOffer getPackage(String tenantId, String productId, String connectionId) {
        Product product = queryService.getProduct(tenantId, productId);
        Map<String, EligibilityProvider.EligibleProduct> eligibleByCode =
                resolveEligible(tenantId, connectionId);
        Map<String, ActivationProvider.ActivePackage> activeByCode =
                resolveActive(tenantId, connectionId);
        return toOffer(product, eligibleByCode.get(product.getSourceProductId()),
                activeByCode.get(product.getSourceProductId()));
    }

    /**
     * List currently active packages / add-ons / VAS for a connection.
     */
    public List<ActivationProvider.ActivePackage> getActivePackages(String tenantId, String connectionId) {
        ActivationProvider provider = resolveProvider(tenantId, activationProviders, "activation");
        return provider.getActivePackages(tenantId, connectionId);
    }

    /**
     * Check whether a connection is eligible for a package.
     */
    public EligibilityProvider.EligibilityResult checkEligibility(String tenantId, String connectionId,
                                                                  String productId, String offerCode) {
        Product product = queryService.getProduct(tenantId, productId);
        EligibilityProvider provider =
                resolveProvider(tenantId, eligibilityProviders, "eligibility");
        return provider.checkEligibility(tenantId, connectionId, product.getSourceProductId(), offerCode);
    }

    /**
     * Activate a package on a connection. The materialized {@code sourceProductId}
     * is the operator product code used for the BSS activation call.
     */
    public ActivationProvider.ActivationResult activate(String tenantId, String connectionId, String productId) {
        Product product = queryService.getProduct(tenantId, productId);
        ActivationProvider provider = resolveProvider(tenantId, activationProviders, "activation");
        return provider.activate(tenantId, connectionId, product.getSourceProductId());
    }

    /**
     * Deactivate a package on a connection.
     */
    public ActivationProvider.ActivationResult deactivate(String tenantId, String connectionId, String productId) {
        Product product = queryService.getProduct(tenantId, productId);
        ActivationProvider provider = resolveProvider(tenantId, activationProviders, "activation");
        return provider.deactivate(tenantId, connectionId, product.getSourceProductId());
    }

    // ================================================================
    // Internals
    // ================================================================

    private Map<String, EligibilityProvider.EligibleProduct> resolveEligible(String tenantId, String connectionId) {
        if (connectionId == null || connectionId.isBlank()) return Map.of();
        try {
            EligibilityProvider provider = resolveProvider(tenantId, eligibilityProviders, "eligibility");
            return provider.getEligibleProducts(tenantId, connectionId).stream()
                    .collect(Collectors.toMap(EligibilityProvider.EligibleProduct::productCode,
                            Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("Eligibility resolution failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, ActivationProvider.ActivePackage> resolveActive(String tenantId, String connectionId) {
        if (connectionId == null || connectionId.isBlank()) return Map.of();
        try {
            ActivationProvider provider = resolveProvider(tenantId, activationProviders, "activation");
            return provider.getActivePackages(tenantId, connectionId).stream()
                    .collect(Collectors.toMap(ActivationProvider.ActivePackage::productCode,
                            Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("Active package resolution failed for tenant={} connection={}: {}",
                    tenantId, connectionId, e.getMessage());
            return Map.of();
        }
    }

    private <T extends ApiAdapter> T resolveProvider(String tenantId, List<T> providers, String capability) {
        return providers.stream()
                .filter(p -> p.getAdapterId() != null && tenantId.equals(p.getAdapterId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "No " + capability + " provider registered for tenant", tenantId));
    }

    private PackageOffer toOffer(Product p,
                                 EligibilityProvider.EligibleProduct eligible,
                                 ActivationProvider.ActivePackage active) {
        return new PackageOffer(
                p.getProductId(),
                p.getSourceProductId(),
                p.getSourceSystem(),
                p.getName(),
                p.getDescription(),
                p.getCategory(),
                p.getSubcategory(),
                p.getLob(),
                p.getConnectionType(),
                p.getPrice(),
                p.getCurrency(),
                p.getValidityDays(),
                p.getAllowances(),
                p.getTerms(),
                p.getImageUrl(),
                p.getBadge(),
                eligible != null,
                eligible != null ? eligible.eligibilityMetadata().toString() : null,
                eligible != null ? eligible.offerCode() : null,
                eligible != null ? eligible.offerExpiry() : null,
                active != null,
                active != null ? active.status() : null,
                active != null ? active.autoRenew() : null
        );
    }

    /**
     * Canonical package offer — a catalog product enriched with the caller's
     * eligibility and subscription state.
     */
    public record PackageOffer(
            String productId,
            String productCode,
            String sourceSystem,
            String name,
            String description,
            String category,
            String subcategory,
            String lob,
            String connectionType,
            BigDecimal price,
            String currency,
            Integer validityDays,
            String allowances,
            String terms,
            String imageUrl,
            String badge,
            Boolean eligible,
            String eligibilityReason,
            String offerCode,
            Instant offerExpiry,
            Boolean active,
            String activeStatus,
            Boolean autoRenew
    ) {}
}