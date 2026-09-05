package com.omobio.platform.common.web;

import com.omobio.platform.common.config.ClientIntegrationConfig;
import com.omobio.platform.common.config.ClientIntegrationRepository;
import com.omobio.platform.common.security.UrlAllowlist;
import com.omobio.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin REST API for managing client/tenant integrations.
 *
 * A "client integration" is the connection configuration between a tenant
 * (Dialog, AIA, ...) and the upstream system that tenant integrates with
 * (BSS, SMSC, policy API, etc.). One tenant can have many integrations,
 * one per upstream system.
 *
 * Endpoints:
 *   GET    /api/v1/admin/integrations?tenantId=...   List integrations for tenant
 *   GET    /api/v1/admin/integrations?tenantId=...&industry=TELCO  Filter by industry
 *   GET    /api/v1/admin/integrations/{id}           Get one
 *   POST   /api/v1/admin/integrations                Create
 *   PUT    /api/v1/admin/integrations/{id}           Update
 *   DELETE /api/v1/admin/integrations/{id}           Delete
 *   POST   /api/v1/admin/integrations/{id}/test      Test connection
 *
 * The Selfcare Studio "Integrations" page calls these endpoints.
 * All credentials are stored encrypted in the DB (at-rest encryption
 * handled by MongoDB encryption-at-rest config).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/integrations")
@RequiredArgsConstructor
public class AdminIntegrationController {

    private final ClientIntegrationRepository repository;
    private final TenantConfigurationService tenantConfig;

    private static String baseUrlOrNull(ClientIntegrationConfig c) {
        return c == null ? null : c.getBaseUrl();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClientIntegrationConfig>>> list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String industry) {
        List<ClientIntegrationConfig> all;
        if (tenantId != null && industry != null) {
            all = repository.findByTenantIdAndIndustry(tenantId, industry);
        } else if (tenantId != null) {
            all = repository.findByTenantId(tenantId);
        } else {
            all = repository.findAll();
        }
        return ResponseEntity.ok(ApiResponse.of(all, ""));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientIntegrationConfig>> get(@PathVariable String id) {
        ClientIntegrationConfig cfg = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Integration", id));
        return ResponseEntity.ok(ApiResponse.of(cfg, ""));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClientIntegrationConfig>> create(
            @RequestBody ClientIntegrationConfig body) {
        UrlAllowlist.validate(baseUrlOrNull(body),
                Arrays.asList(UrlAllowlist.DEFAULT_ALLOWED_HOSTS.toArray(new String[0])));
        body.setId(UUID.randomUUID().toString());
        body.setCreatedAt(Instant.now());
        body.setUpdatedAt(Instant.now());
        ClientIntegrationConfig saved = repository.save(body);
        tenantConfig.invalidateIntegration(body.getTenantId(), body.getIntegrationType());
        log.info("Integration created: tenant={}, industry={}, type={}, id={}",
                body.getTenantId(), body.getIndustry(), body.getIntegrationType(), saved.getId());
        return ResponseEntity.ok(ApiResponse.of(saved, ""));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientIntegrationConfig>> update(
            @PathVariable String id,
            @RequestBody ClientIntegrationConfig body) {
        UrlAllowlist.validate(baseUrlOrNull(body),
                Arrays.asList(UrlAllowlist.DEFAULT_ALLOWED_HOSTS.toArray(new String[0])));
        ClientIntegrationConfig existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Integration", id));
        existing.setBaseUrl(body.getBaseUrl());
        existing.setAuthType(body.getAuthType());
        existing.setCredentials(body.getCredentials());
        existing.setFieldMapping(body.getFieldMapping());
        existing.setAdvanced(body.getAdvanced());
        existing.setStatus(body.getStatus());
        existing.setMetadata(body.getMetadata());
        existing.setProviderClass(body.getProviderClass());
        existing.setIndustry(body.getIndustry());
        existing.setUpdatedAt(Instant.now());
        ClientIntegrationConfig saved = repository.save(existing);
        tenantConfig.invalidateIntegration(existing.getTenantId(), existing.getIntegrationType());
        log.info("Integration updated: tenant={}, industry={}, type={}, id={}",
                existing.getTenantId(), existing.getIndustry(), existing.getIntegrationType(), id);
        return ResponseEntity.ok(ApiResponse.of(saved, ""));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        ClientIntegrationConfig existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Integration", id));
        repository.deleteById(id);
        tenantConfig.invalidateIntegration(existing.getTenantId(), existing.getIntegrationType());
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }

    /**
     * Health check the integration by calling its base URL with a /health or
     * /status probe.
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(@PathVariable String id) {
        ClientIntegrationConfig cfg = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Integration", id));

        String baseUrl = cfg.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.of(Map.of("status", "ERROR", "message", "No base URL configured"), ""));
        }

        long start = System.currentTimeMillis();
        try {
            String probeUrl = baseUrl.replaceAll("/+$", "") + "/health";
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Void> req = new org.springframework.http.HttpEntity<>(headers);

            var resp = rt.exchange(probeUrl, org.springframework.http.HttpMethod.GET, req, String.class);
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> result = Map.of(
                    "status", "HEALTHY",
                    "message", "Connection successful",
                    "integrationId", id,
                    "responseTimeMs", elapsed,
                    "probeUrl", probeUrl
            );
            cfg.setHealth(ClientIntegrationConfig.HealthStatus.builder()
                    .status("HEALTHY")
                    .lastChecked(Instant.now())
                    .responseTimeMs(elapsed)
                    .build());
            cfg.setUpdatedAt(Instant.now());
            repository.save(cfg);
            return ResponseEntity.ok(ApiResponse.of(result, ""));

        } catch (org.springframework.web.client.ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> result = Map.of(
                    "status", "UNHEALTHY",
                    "message", "Connection failed: " + e.getMostSpecificCause().getMessage(),
                    "integrationId", id,
                    "responseTimeMs", elapsed
            );
            cfg.setHealth(ClientIntegrationConfig.HealthStatus.builder()
                    .status("UNHEALTHY")
                    .lastChecked(Instant.now())
                    .lastError(e.getMostSpecificCause().getMessage())
                    .build());
            cfg.setUpdatedAt(Instant.now());
            repository.save(cfg);
            return ResponseEntity.ok(ApiResponse.of(result, ""));
        }
    }
}
