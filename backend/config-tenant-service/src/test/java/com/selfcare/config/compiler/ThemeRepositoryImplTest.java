package com.selfcare.config.compiler;

import com.selfcare.config.domain.ThemeDocument;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeRepositoryImplTest {

    private final ThemeRepositoryImpl repository = new ThemeRepositoryImpl(null);

    private Map<String, Object> seedTypography() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("fontFamily", "Roboto");
        t.put("body", "16px");
        t.put("h1", "32px");
        t.put("h2", "24px");
        t.put("h3", "20px");
        t.put("tiny", "12px");
        t.put("small", "14px");
        t.put("weightRegular", 400);
        t.put("weightMedium", 500);
        t.put("weightBold", 700);
        return t;
    }

    @Test
    void surfacesBrandingLogoAsLogoUrl() {
        ThemeDocument.BrandAsset logo = ThemeDocument.BrandAsset.builder()
                .assetId("dialog-logo")
                .url("https://cdn.selfcare.lk/dialog/logo.svg")
                .alt("Dialog")
                .width(200)
                .height(60)
                .build();
        ThemeDocument theme = ThemeDocument.builder()
                .name("dialog-default")
                .branding(ThemeDocument.Branding.builder().logo(logo).build())
                .baseTokens(ThemeDocument.ThemeTokens.builder()
                        .colors(Map.of("primary", "#65246E"))
                        .build())
                .build();

        Map<String, Object> flat = repository.flatten(theme);

        assertEquals("https://cdn.selfcare.lk/dialog/logo.svg", flat.get("logoUrl"));
        assertEquals("#65246E", ((Map<?, ?>) flat.get("colors")).get("primary"));
    }

    @Test
    void fallsBackToAppIconWhenLogoMissing() {
        ThemeDocument theme = ThemeDocument.builder()
                .name("dialog-default")
                .branding(ThemeDocument.Branding.builder()
                        .appIcon(ThemeDocument.BrandAsset.builder()
                                .url("https://cdn.selfcare.lk/dialog/icon-512.png")
                                .build())
                        .favicon(ThemeDocument.BrandAsset.builder()
                                .url("https://cdn.selfcare.lk/dialog/favicon.ico")
                                .build())
                        .build())
                .baseTokens(ThemeDocument.ThemeTokens.builder().colors(Map.of()).build())
                .build();

        Map<String, Object> flat = repository.flatten(theme);

        assertEquals("https://cdn.selfcare.lk/dialog/icon-512.png", flat.get("logoUrl"));
    }

    @Test
    void acceptsLogoFromJsonLikeSeed() throws Exception {
        String json = """
                {
                  "name": "dialog-default",
                  "baseTokens": { "colors": { "primary": "#65246E" } },
                  "branding": {
                    "logo":    { "assetId": "dialog-logo",    "url": "https://cdn.selfcare.lk/dialog/logo.svg",    "alt": "Dialog", "width": 200, "height": 60 },
                    "appIcon": { "assetId": "dialog-icon",    "url": "https://cdn.selfcare.lk/dialog/icon-512.png", "alt": "Dialog Axiata" },
                    "favicon": { "assetId": "dialog-favicon", "url": "https://cdn.selfcare.lk/dialog/favicon.ico",  "alt": "Dialog favicon" },
                    "splash":  { "assetId": "dialog-splash",  "url": "https://cdn.selfcare.lk/dialog/splash.png",   "alt": "Dialog splash" },
                    "loginLogo": { "assetId": "dialog-logo",  "url": "https://cdn.selfcare.lk/dialog/logo.svg",    "alt": "Dialog" }
                  },
                  "overrideTokens": {
                    "darkMode": {
                      "colors": { "primary": "#B76AC4", "background": "#121212" },
                      "buttons": { "primary": { "bg": "#B76AC4", "text": "#FFFFFF" } }
                    }
                  }
                }
                """;
        ThemeDocument theme = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readValue(json, ThemeDocument.class);

        assertNull(theme.getBaseTokens().getDarkMode());
        Map<String, Object> flat = repository.flatten(theme);

        assertEquals("dialog-default", flat.get("name"));
        assertEquals("https://cdn.selfcare.lk/dialog/logo.svg", flat.get("logoUrl"));
        assertEquals("#65246E", ((Map<?, ?>) flat.get("colors")).get("primary"));
        Map<?, ?> dark = (Map<?, ?>) flat.get("dark");
        assertEquals("#121212", ((Map<?, ?>) dark.get("colors")).get("background"));
        assertEquals("#B76AC4", ((Map<?, ?>) ((Map<?, ?>) dark.get("buttons")).get("primary")).get("bg"));
    }

    @Test
    void mergesDarkModeTokensIntoManifestDarkLayer() {
        ThemeDocument.ThemeTokens base = ThemeDocument.ThemeTokens.builder()
                .colors(Map.of("primary", "#65246E", "background", "#FFFFFF"))
                .typography(seedTypography())
                .build();
        ThemeDocument.ThemeTokens dark = ThemeDocument.ThemeTokens.builder()
                .colors(Map.of("primary", "#B76AC4", "background", "#121212"))
                .typography(seedTypography())
                .build();
        ThemeDocument.ThemeTokens override = ThemeDocument.ThemeTokens.builder()
                .darkMode(dark)
                .build();

        Map<String, Object> flat = repository.flatten(
                ThemeDocument.builder().name("dialog-default").baseTokens(base).overrideTokens(override).build());

        assertEquals("#65246E", ((Map<?, ?>) flat.get("colors")).get("primary"));
        Map<?, ?> darkLayer = (Map<?, ?>) flat.get("dark");
        assertEquals("#B76AC4", ((Map<?, ?>) darkLayer.get("colors")).get("primary"));
        assertEquals("Roboto", ((Map<?, ?>) darkLayer.get("typography")).get("fontFamily"));
        assertTrue(flat.containsKey("typography"));
        assertFalse(flat.containsKey("logoUrl"));
        assertEquals("system", flat.get("mode"));
    }
}