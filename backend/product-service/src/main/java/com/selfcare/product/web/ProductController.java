package com.selfcare.product.web;

import com.selfcare.platform.common.adapter.ActivationProvider;
import com.selfcare.platform.common.adapter.EligibilityProvider;
import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.dto.PaginationResponse;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import com.selfcare.product.domain.Offer;
import com.selfcare.product.domain.Product;
import com.selfcare.product.service.PackageSubscriptionService;
import com.selfcare.product.service.ProductQueryService;
import com.selfcare.product.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Product catalog REST API.
 *
 * Endpoints:
 *   GET    /api/v1/products                       — List products (paginated, filterable)
 *   GET    /api/v1/products/{id}                  — Get product detail
 *   GET    /api/v1/products/categories            — List categories with counts
 *   GET    /api/v1/products/featured              — Featured products (top N by badge)
 *   GET    /api/v1/products/{id}/offers           — Active offers for a product
 *   GET    /api/v1/offers/active                  — All active offers
 *   GET    /api/v1/offers/recommendations         — Personalized recommendations
 *   POST   /api/v1/admin/products/refresh         — Trigger materialization (admin)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog, offers, packages, recommendations")
public class ProductController {

    private final ProductQueryService queryService;
    private final RecommendationService recommendationService;
    private final PackageSubscriptionService packageService;

    @GetMapping("/products")
    @Operation(summary = "List products", description = "Paginated, filterable product catalog")
    public ResponseEntity<ApiResponse<PaginationResponse<Product>>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String lob,
            @RequestParam(required = false) String connectionType,
            @RequestParam(required = false) String badge,
            @RequestParam(required = false) String search,
            PaginationRequest pagination) {

        String tenantId = TenantContext.get().getTenantId();
        log.debug("List products: tenant={}, category={}, lob={}, badge={}",
                tenantId, category, lob, badge);

        PaginationResponse<Product> response = queryService.listProducts(
                tenantId, category, lob, connectionType, badge, search, pagination);

        return ResponseEntity.ok(ApiResponse.of(response, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get product detail")
    public ResponseEntity<ApiResponse<Product>> getProduct(@PathVariable String productId) {
        String tenantId = TenantContext.get().getTenantId();
        Product product = queryService.getProduct(tenantId, productId);
        return ResponseEntity.ok(ApiResponse.of(product, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/categories")
    @Operation(summary = "List categories with counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getCategories() {
        String tenantId = TenantContext.get().getTenantId();
        Map<String, Long> categories = queryService.getCategoryCounts(tenantId);
        return ResponseEntity.ok(ApiResponse.of(categories, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/featured")
    @Operation(summary = "Featured products", description = "Top products by badge (POPULAR, NEW, RECOMMENDED)")
    public ResponseEntity<ApiResponse<List<Product>>> getFeatured(
            @RequestParam(defaultValue = "10") int limit) {
        String tenantId = TenantContext.get().getTenantId();
        List<Product> featured = queryService.getFeatured(tenantId, limit);
        return ResponseEntity.ok(ApiResponse.of(featured, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/{productId}/offers")
    @Operation(summary = "List active offers for a product")
    public ResponseEntity<ApiResponse<List<Offer>>> getProductOffers(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.of(
                recommendationService.activeOffersForProduct(productId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/offers/active")
    @Operation(summary = "List all active offers for the tenant")
    public ResponseEntity<ApiResponse<List<RecommendationService.Recommendation>>> getActiveOffers() {
        return ResponseEntity.ok(ApiResponse.of(
                recommendationService.activeOffers(),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/offers/recommendations")
    @Operation(summary = "Personalized recommendations for the current user")
    public ResponseEntity<ApiResponse<List<RecommendationService.Recommendation>>> getRecommendations(
            @RequestParam(required = false) String connectionType,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) Map<String, Long> usage) {
        return ResponseEntity.ok(ApiResponse.of(
                recommendationService.recommend(connectionType, segment, usage),
                TenantContext.get().getCorrelationId()));
    }

    // ================================================================
    // Package subscription (browse / eligibility / activation)
    // ================================================================

    @GetMapping("/products/packages")
    @Operation(summary = "Browse packages",
            description = "Catalog packages filtered by category/LOB, enriched with the caller's "
                    + "eligibility and subscription state when connectionId is supplied")
    public ResponseEntity<ApiResponse<List<PackageSubscriptionService.PackageOffer>>> browsePackages(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String lob,
            @RequestParam(required = false) String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                packageService.browsePackages(tenantId, category, lob, connectionId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/packages/{productId}")
    @Operation(summary = "Get a single package with caller eligibility/state")
    public ResponseEntity<ApiResponse<PackageSubscriptionService.PackageOffer>> getPackage(
            @PathVariable String productId,
            @RequestParam(required = false) String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                packageService.getPackage(tenantId, productId, connectionId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/active")
    @Operation(summary = "List active packages for a connection")
    public ResponseEntity<ApiResponse<List<ActivationProvider.ActivePackage>>> getActivePackages(
            @RequestParam String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                packageService.getActivePackages(tenantId, connectionId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/products/{productId}/eligibility")
    @Operation(summary = "Check eligibility for a package")
    public ResponseEntity<ApiResponse<EligibilityProvider.EligibilityResult>> checkPackageEligibility(
            @PathVariable String productId,
            @RequestParam String connectionId,
            @RequestParam(required = false) String offerCode) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                packageService.checkEligibility(tenantId, connectionId, productId, offerCode),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/products/{productId}/activate")
    @Operation(summary = "Activate a package on a connection")
    public ResponseEntity<ApiResponse<ActivationProvider.ActivationResult>> activatePackage(
            @PathVariable String productId,
            @RequestParam String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                packageService.activate(tenantId, connectionId, productId),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/products/{productId}/deactivate")
    @Operation(summary = "Deactivate a package on a connection")
    public ResponseEntity<ApiResponse<ActivationProvider.ActivationResult>> deactivatePackage(
            @PathVariable String productId,
            @RequestParam String connectionId) {
        String tenantId = TenantContext.get().getTenantId();
        return ResponseEntity.ok(ApiResponse.of(
                packageService.deactivate(tenantId, connectionId, productId),
                TenantContext.get().getCorrelationId()));
    }
}
