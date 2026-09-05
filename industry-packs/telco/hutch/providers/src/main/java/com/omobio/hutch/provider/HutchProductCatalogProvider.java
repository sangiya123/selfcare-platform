package com.omobio.hutch.provider;

import com.omobio.platform.common.adapter.ApiAdapter;
import com.omobio.platform.common.adapter.RegisterAdapter;
import com.omobio.platform.common.tenant.TenantContextBridge;
import com.omobio.product.adapter.ProductCatalogProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Hutch Product Catalog Provider — fetches Hutch's product/offer catalog
 * via the Hutch BSS product endpoint.
 *
 * <p>Implements the {@link ProductCatalogProvider} contract. Hutch uses
 * API-key authentication; product data is wrapped in a {@code data}
 * envelope and uses a flatter shape than Dialog.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link HutchHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Hutch Catalog.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "hutch-lk", providerInterface = ProductCatalogProvider.class)
@RequiredArgsConstructor
public class HutchProductCatalogProvider implements ApiAdapter, ProductCatalogProvider {

    private static final String INTEGRATION_TYPE = "HUTCH_BSS";
    private static final String ADAPTER_ID = "hutch-lk";
    private static final String SOURCE_SYSTEM = "HUTCH";

    /** Per-category catalog path → LOB mapping. */
    private static final Map<String, String> CATEGORIES = Map.of(
            "data", "DATA",
            "voice", "VOICE",
            "combo", "COMBO",
            "vas", "ADDON"
    );

    private final HutchHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    @Override
    public String getSourceSystem() {
        return SOURCE_SYSTEM;
    }

    @Override
    public List<RawProduct> fetchAllProducts() {
        return fetchAllProducts(TenantContextBridge.currentTenantId());
    }

    public List<RawProduct> fetchAllProducts(String tenantId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("No active Hutch Catalog integration for tenant={}, returning empty", tenantId);
            return Collections.emptyList();
        }

        var products = new ArrayList<RawProduct>();
        CATEGORIES.forEach((path, lob) -> products.addAll(fetchCategory(tenantId, cfg, path, lob)));
        log.info("Hutch catalog: fetched {} products for tenant={}", products.size(), tenantId);
        return products;
    }

    @Override
    public RawProduct fetchProduct(String sourceProductId) {
        return fetchProduct(TenantContextBridge.currentTenantId(), sourceProductId);
    }

    public RawProduct fetchProduct(String tenantId, String sourceProductId) {
        HutchHttpClient.HutchConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) return null;
        try {
            String path = "/api/v1/products/" + sourceProductId;
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, null);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return mapToRawProduct(data, "MIXED");
            }
            return null;
        } catch (HutchApiException e) {
            log.warn("Hutch product fetch failed for id={}: {}", sourceProductId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Internals
    // ================================================================

    @SuppressWarnings("unchecked")
    private List<RawProduct> fetchCategory(String tenantId, HutchHttpClient.HutchConfig cfg,
                                          String path, String lob) {
        try {
            String fullPath = "/api/v1/catalog/" + path;
            Map<String, Object> resp = httpClient.get(tenantId, cfg, fullPath, null, null);
            if (resp == null) return Collections.emptyList();
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map<?, ?> dm)) return Collections.emptyList();
            Object itemsObj = ((Map<String, Object>) dm).get("items");
            if (!(itemsObj instanceof List<?> items)) return Collections.emptyList();

            var products = new ArrayList<RawProduct>();
            for (Object o : items) {
                if (o instanceof Map<?, ?> rawMap) {
                    products.add(mapToRawProduct((Map<String, Object>) rawMap, lob));
                }
            }
            return products;
        } catch (HutchApiException e) {
            log.warn("Failed to fetch Hutch catalog category {}: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    private RawProduct mapToRawProduct(Map<String, Object> item, String lob) {
        // Build allowances JSON array directly; RawProduct.allowances is a String field
        String allowancesJson = buildAllowancesJson(item);

        BigDecimal price = BigDecimal.ZERO;
        Object p = item.get("price");
        if (p instanceof Number num) price = BigDecimal.valueOf(num.doubleValue());
        else if (p instanceof String s) {
            try { price = new BigDecimal(s); } catch (NumberFormatException ignored) {}
        }

        Integer validityDays = item.get("validity") instanceof Number vd ? vd.intValue() : null;

        return RawProduct.builder()
                .sourceProductId((String) item.get("id"))
                .name((String) item.get("name"))
                .description((String) item.get("description"))
                .category((String) item.get("category"))
                .subcategory((String) item.get("subCategory"))
                .lob(lob)
                .connectionType((String) item.get("connectionType"))
                .price(price)
                .currency("LKR")
                .validityDays(validityDays)
                .allowances(allowancesJson)
                .terms((String) item.get("terms"))
                .imageUrl((String) item.get("imageUrl"))
                .badge((String) item.get("badge"))
                .displayOrder(item.get("displayOrder") instanceof Number d ? d.intValue() : null)
                .status((String) item.getOrDefault("status", "ACTIVE"))
                .tags(extractTags(item))
                .lastModified(parseInstant(item.get("lastModified")))
                .build();
    }

    /**
     * Build a JSON array string for allowances from the raw API response item.
     * RawProduct.allowances is a String field holding JSON.
     */
    private String buildAllowancesJson(Map<String, Object> item) {
        var sb = new StringBuilder("[");
        boolean first = true;

        if (item.get("dataMb") instanceof Number dm) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"DATA\",\"quantity\":").append(dm.longValue() * 1_048_576L).append(",\"unit\":\"B\"}");
        }
        if (item.get("voiceMinutes") instanceof Number vm) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"VOICE\",\"quantity\":").append(vm.longValue() * 60L).append(",\"unit\":\"s\"}");
        }
        if (item.get("smsCount") instanceof Number sc) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"SMS\",\"quantity\":").append(sc.longValue()).append(",\"unit\":\"SMS\"}");
        }

        return sb.length() > 1 ? sb.append(']').toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractTags(Map<String, Object> item) {
        Object tags = item.get("tags");
        if (tags instanceof List<?> l) {
            var out = new ArrayList<String>();
            for (Object o : l) out.add(o.toString());
            return out;
        }
        if (tags instanceof String s) {
            return Arrays.asList(s.split(","));
        }
        return Collections.emptyList();
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (value instanceof String s) {
            try { return Instant.parse(s); } catch (Exception ignored) {}
        }
        return null;
    }
}
