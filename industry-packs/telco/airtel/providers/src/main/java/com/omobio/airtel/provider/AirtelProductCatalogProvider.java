package com.omobio.airtel.provider;

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
 * Airtel Product Catalog Provider — fetches Airtel's product/offer catalog
 * via the Airtel Money gateway catalog endpoint.
 *
 * <p>Implements the {@link ProductCatalogProvider} contract. Airtel uses OAuth2
 * bearer tokens (obtained via {@link AirtelHttpClient} token caching) and
 * wraps responses in a {@code { data: { ... } }} envelope.</p>
 *
 * <p>Per-tenant config loaded from MongoDB via {@link AirtelHttpClient}.
 * Configure via Selfcare Studio admin: Integrations &gt; Airtel Gateway.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "airtel-lk", providerInterface = ProductCatalogProvider.class)
@RequiredArgsConstructor
public class AirtelProductCatalogProvider implements ApiAdapter, ProductCatalogProvider {

    private static final String INTEGRATION_TYPE = "AIRTEL_GATEWAY";
    private static final String ADAPTER_ID = "airtel-lk";
    private static final String SOURCE_SYSTEM = "AIRTEL";

    /** Per-category catalog path → LOB mapping. */
    private static final Map<String, String> CATEGORIES = Map.of(
            "data", "DATA",
            "voice", "VOICE",
            "combo", "COMBO",
            "vas", "ADDON"
    );

    private final AirtelHttpClient httpClient;

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
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) {
            log.warn("No active Airtel Catalog integration for tenant={}, returning empty", tenantId);
            return Collections.emptyList();
        }
        var products = new ArrayList<RawProduct>();
        CATEGORIES.forEach((path, lob) -> products.addAll(fetchCategory(tenantId, cfg, path, lob)));
        log.info("Airtel catalog: fetched {} products for tenant={}", products.size(), tenantId);
        return products;
    }

    @Override
    public RawProduct fetchProduct(String sourceProductId) {
        return fetchProduct(TenantContextBridge.currentTenantId(), sourceProductId);
    }

    public RawProduct fetchProduct(String tenantId, String sourceProductId) {
        AirtelHttpClient.AirtelConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        if (!cfg.isValid()) return null;
        try {
            String path = "/v1/osp/catalog/product/" + sourceProductId;
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.get(tenantId, cfg, path, null, token);
            if (resp == null) return null;
            Object dataObj = resp.get("data");
            if (dataObj instanceof Map<?, ?> d) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) d;
                return mapToRawProduct(data, "MIXED");
            }
            return null;
        } catch (AirtelApiException e) {
            log.warn("Airtel product fetch failed for id={}: {}", sourceProductId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Internals
    // ================================================================

    @SuppressWarnings("unchecked")
    private List<RawProduct> fetchCategory(String tenantId, AirtelHttpClient.AirtelConfig cfg,
                                          String path, String lob) {
        try {
            String fullPath = "/v1/osp/catalog/" + path;
            String token = httpClient.getAccessToken(tenantId, cfg.baseUrl(), cfg.clientId(), cfg.clientSecret());
            Map<String, Object> resp = httpClient.get(tenantId, cfg, fullPath, null, token);
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
        } catch (AirtelApiException e) {
            log.warn("Failed to fetch Airtel catalog category {}: {}", path, e.getMessage());
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

        Integer validityDays = item.get("validityDays") instanceof Number vd ? vd.intValue() : null;

        return RawProduct.builder()
                .sourceProductId((String) item.get("productId"))
                .name((String) item.get("displayName"))
                .description((String) item.get("description"))
                .category((String) item.get("category"))
                .subcategory((String) item.get("subcategory"))
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

        if (item.get("dataBytes") instanceof Number db) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"DATA\",\"quantity\":").append(db.longValue()).append(",\"unit\":\"B\"}");
        }
        if (item.get("voiceSeconds") instanceof Number vs) {
            if (!first) sb.append(','); first = false;
            sb.append("{\"type\":\"VOICE\",\"quantity\":").append(vs.longValue()).append(",\"unit\":\"s\"}");
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
