package com.selfcare.config.compiler;

import com.selfcare.config.domain.ThemeDocument;
import com.selfcare.config.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a theme reference (e.g. "dialog-default@17") to the FLATTENED
 * ManifestTheme map consumed by the mobile/web apps.
 *
 * The app contract is a flat object — colors, typography and layout tokens at
 * the root (see mobile ManifestTheme). The Mongo document wraps tokens in
 * {@code baseTokens}, so this repository unwraps and merges override tokens
 * before the compiler embeds the result in the immutable manifest.
 */
@Component
@RequiredArgsConstructor
public class ThemeRepositoryImpl implements ConfigCompiler.ThemeRepository {

    private final ThemeService themeService;

    @Override
    public Optional<Map<String, Object>> findByRef(String ref) {
        if (ref == null || ref.isBlank()) return Optional.empty();
        // ref format: "name@version" (version is optional)
        String[] parts = ref.split("@");
        String name = parts[0];
        String version = parts.length > 1 ? parts[1] : "1.0.0";

        // Resolve tenant from current request context
        String tenantId = com.selfcare.platform.common.tenant.TenantContext.get().getTenantId();
        if (tenantId == null || "UNKNOWN".equals(tenantId)) {
            return Optional.empty();
        }

        try {
            var theme = themeService.getByRef(tenantId, name, version);
            return Optional.of(flatten(theme));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Unwrap ThemeDocument into the flat ManifestTheme map the app renders.
     * Colors: base merged with override (override wins). Typography: base fused
     * with the app's xs..4xl size scale. Radius/spacing/borderRadius copied
     * through. Layout/elevation/logoUrl surfaced when present in the document.
     */
    Map<String, Object> flatten(ThemeDocument theme) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("name", theme.getName());

        ThemeDocument.ThemeTokens base = theme.getBaseTokens();
        ThemeDocument.ThemeTokens override = theme.getOverrideTokens();

        // Colors — base merged with override (override wins).
        Map<String, String> colors = new LinkedHashMap<>();
        if (base != null && base.getColors() != null) colors.putAll(base.getColors());
        if (override != null && override.getColors() != null) colors.putAll(override.getColors());
        if (!colors.isEmpty()) flat.put("colors", colors);

        // Typography — translate legacy h1/h2/body keys onto the app's xs..4xl
        // size scale with explicit weights, so ManifestTheme resolves correctly.
        Map<String, Object> typo = base != null && base.getTypography() != null
                ? resolveTypography(base.getTypography())
                : new LinkedHashMap<>();
        // Fallback to app-neutral Roboto scale when the doc carries no typography.
        if (typo.get("fontFamily") == null) typo.put("fontFamily", "Roboto");
        if (typo.get("sizes") == null) {
            typo.put("sizes", Map.of(
                    "xs", "12px", "sm", "14px", "base", "16px", "lg", "18px",
                    "xl", "20px", "2xl", "24px", "3xl", "30px", "4xl", "36px"));
        }
        if (typo.get("weights") == null) {
            typo.put("weights", Map.of("normal", 400, "medium", 500, "semibold", 600, "bold", 700));
        }
        flat.put("typography", typo);

        if (base != null) {
            if (base.getSpacing() != null && !base.getSpacing().isEmpty()) {
                flat.put("spacing", new java.util.ArrayList<>(base.getSpacing().values()));
            }
            if (base.getBorderRadius() != null && !base.getBorderRadius().isEmpty()) {
                flat.put("borderRadius", base.getBorderRadius());
            }
            if (base.getElevation() != null && !base.getElevation().isEmpty()) {
                flat.put("elevation", base.getElevation());
            }
            if (base.getButtons() != null && !base.getButtons().isEmpty()) {
                flat.put("buttons", base.getButtons());
            }
        }
        // Button style tokens — base merged with override (override wins).
        if (override != null && override.getButtons() != null && !override.getButtons().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) flat.get("buttons");
            if (existing != null) {
                Map<String, Object> merged = new LinkedHashMap<>(existing);
                merged.putAll(override.getButtons());
                flat.put("buttons", merged);
            } else {
                flat.put("buttons", override.getButtons());
            }
        }

        // Branding — operator logo surfaces as the compiled manifest logoUrl (AppHeader).
        if (theme.getBranding() != null) {
            ThemeDocument.BrandAsset logo = theme.getBranding().getLogo();
            String logoUrl = logo != null ? logo.getUrl() : null;
            if (logoUrl == null || logoUrl.isBlank()) {
                ThemeDocument.BrandAsset appIcon = theme.getBranding().getAppIcon();
                if (appIcon != null) logoUrl = appIcon.getUrl();
            }
            if (logoUrl != null && !logoUrl.isBlank()) flat.put("logoUrl", logoUrl);
        }

        // Dark-mode override layer — overrideTokens.darkMode -> manifest `dark`.
        if (override != null && override.getDarkMode() != null) {
            ThemeDocument.ThemeTokens dark = override.getDarkMode();
            Map<String, Object> darkLayer = new LinkedHashMap<>();
            if (dark.getColors() != null) darkLayer.put("colors", dark.getColors());
            if (dark.getButtons() != null && !dark.getButtons().isEmpty()) {
                darkLayer.put("buttons", dark.getButtons());
            }
            Map<String, Object> darkTypo = dark.getTypography() != null
                    ? resolveTypography(dark.getTypography())
                    : new LinkedHashMap<>();
            if (!darkTypo.isEmpty()) darkLayer.put("typography", darkTypo);
            if (!darkLayer.isEmpty()) flat.put("dark", darkLayer);
        }

        if (flat.get("radius") == null) {
            Object r = base != null && base.getBorderRadius() != null
                    ? base.getBorderRadius().get("lg")
                    : null;
            flat.put("radius", r != null ? r + "px" : "12px");
        }
        Object m = flat.get("mode");
        flat.put("mode", m instanceof String s && (s.equals("light") || s.equals("dark")) ? s : "system");
        return flat;
    }

    /**
     * Translate a ThemeTokens typography map (legacy h1/h2/body keys) onto the
     * app's xs..4xl size scale with explicit weights. Returns an empty map when
     * the source carries no typography tokens.
     */
    private Map<String, Object> resolveTypography(Map<String, Object> src) {
        Map<String, Object> typo = new LinkedHashMap<>();
        if (src.get("fontFamily") != null) typo.put("fontFamily", src.get("fontFamily"));
        Map<String, String> sizes = new LinkedHashMap<>();
        String[] fs = {"xs", "sm", "base", "lg", "xl", "2xl", "3xl", "4xl"};
        String[] legacy = {"tiny", "small", "body", "body", "h3", "h2", "h1", "h1"};
        for (int i = 0; i < fs.length; i++) {
            Object v = src.get(fs[i]) != null ? src.get(fs[i]) : src.get(legacy[i]);
            if (v != null && !String.valueOf(v).isEmpty()) sizes.put(fs[i], String.valueOf(v));
        }
        if (!sizes.isEmpty()) typo.put("sizes", sizes);
        Map<String, Number> weights = new LinkedHashMap<>();
        Object wb = src.get("weightBold") != null ? src.get("weightBold") : src.get("weight_700");
        Object wm = src.get("weightMedium") != null ? src.get("weightMedium") : src.get("weight_500");
        Object wr = src.get("weightRegular") != null ? src.get("weightRegular") : src.get("weight_400");
        if (wr != null) weights.put("normal", ((Number) wr).intValue());
        if (wm != null) weights.put("medium", ((Number) wm).intValue());
        if (wb != null) weights.put("bold", ((Number) wb).intValue());
        if (!weights.isEmpty()) typo.put("weights", weights);
        return typo;
    }
}