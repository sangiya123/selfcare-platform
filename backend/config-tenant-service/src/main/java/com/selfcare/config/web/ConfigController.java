package com.selfcare.config.web;

import com.selfcare.config.compiler.ConfigCompiler;
import com.selfcare.config.service.LayoutService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public config API — used by the mobile app and web app to fetch
 * the compiled manifest for a tenant.
 *
 * Mobile/web calls:
 *   GET /api/v1/config/manifest?environment=prod&experience=home&profileKey=mobile_prepaid
 *
 * Returns the compiled, immutable manifest (theme + navigation + sections).
 * Cached server-side and client-side (ETag).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Config", description = "Public config/manifest serving for mobile/web apps")
public class ConfigController {

    private final LayoutService layoutService;

    @GetMapping("/manifest")
    @Operation(summary = "Get compiled manifest",
               description = "Returns the compiled manifest for the current tenant, environment, experience, and profile key")
    public ResponseEntity<ApiResponse<ConfigCompiler.CompiledManifest>> getManifest(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @Parameter(description = "Environment: dev, qa, staging, reg, prod")
            @RequestParam(defaultValue = "prod") String environment,
            @Parameter(description = "Experience: home, packages, bills, support, profile, ...")
            @RequestParam(defaultValue = "home") String experience,
            @Parameter(description = "Profile key: e.g. mobile_prepaid_youth, mobile_postpaid, insurance_life")
            @RequestParam(defaultValue = "default") String profileKey,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        String tenantId = tenantHeader != null ? tenantHeader : TenantContext.get().getTenantId();
        log.info("Manifest request: tenant={}, env={}, experience={}, profile={}",
                tenantId, environment, experience, profileKey);

        try {
            ConfigCompiler.CompiledManifest manifest = layoutService.getPublishedManifest(
                    tenantId, environment, experience, profileKey);

            String etag = "\"" + manifest.getManifestId() + "\"";
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304).build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.ETAG, etag)
                    .header("X-Config-Version", String.valueOf(manifest.getConfigVersion()))
                    .header("X-Manifest-Id", manifest.getManifestId())
                    .header("Cache-Control", "public, max-age=300, stale-while-revalidate=600")
                    .header("Vary", "X-Tenant-Id, Accept-Language")
                    .body(ApiResponse.of(manifest, TenantContext.get().getCorrelationId()));
        } catch (Exception e) {
            log.warn("Manifest not found for tenant={}, env={}, experience={}, profile={}: {}",
                    tenantId, environment, experience, profileKey, e.getMessage());
            log.debug("Manifest failure detail", e);
            return ResponseEntity.status(404)
                    .body(ApiResponse.of(null, TenantContext.get().getCorrelationId()));
        }
    }

    @GetMapping("/experiences")
    @Operation(summary = "List available experiences",
               description = "Returns the list of experience+profileKeys for which a published layout exists")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listExperiences(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        String tenantId = tenantHeader != null ? tenantHeader : TenantContext.get().getTenantId();
        var published = layoutService.listPublished(tenantId);
        Map<String, Object> body = Map.of(
                "tenantId", tenantId,
                "experiences", published.stream().map(p -> Map.of(
                        "experience", p.getExperience(),
                        "profileKey", p.getProfileKey(),
                        "version", p.getConfigVersion()
                )).toList()
        );
        return ResponseEntity.ok(ApiResponse.of(body, TenantContext.get().getCorrelationId()));
    }
}
