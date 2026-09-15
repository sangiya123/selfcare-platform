package com.selfcare.platform.common.tenant;

import com.selfcare.platform.common.config.ClientIntegrationConfig;
import com.selfcare.platform.common.config.ClientIntegrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Tenant configuration service — central read-only access to tenant-level
 * configuration stored in MongoDB.
 *
 * All client/tenant-specific URLs, credentials, and integration settings are
 * fetched from here. The admin portal's "Integrations" page writes to
 * the same MongoDB collections — admin changes propagate automatically.
 *
 * Caching:
 *   - Integrations cached in Redis with short TTL (5 min)
 *   - Invalidation via Spring's cache abstraction on write
 *
 * Multi-tenant / multi-industry safe: every method takes tenantId as a
 * parameter and never reads from a ThreadLocal or static state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantConfigurationService {

    private final ClientIntegrationRepository integrationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String INTEGRATION_CACHE_PREFIX = "selfcare:tenant:integration:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Get an integration config for a tenant + integration type.
     * Returns empty if not configured.
     */
    public Optional<ClientIntegrationConfig> getIntegration(String tenantId, String integrationType) {
        if (tenantId == null || integrationType == null) {
            return Optional.empty();
        }
        String cacheKey = INTEGRATION_CACHE_PREFIX + tenantId + ":" + integrationType;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof ClientIntegrationConfig) {
            return Optional.of((ClientIntegrationConfig) cached);
        }

        Optional<ClientIntegrationConfig> result = integrationRepository
                .findByTenantIdAndIntegrationType(tenantId, integrationType);

        result.ifPresent(cfg -> redisTemplate.opsForValue().set(cacheKey, cfg, CACHE_TTL));
        return result;
    }

    /**
     * Get all integrations for a tenant.
     */
    public List<ClientIntegrationConfig> getAllIntegrations(String tenantId) {
        return integrationRepository.findByTenantId(tenantId);
    }

    /**
     * Get all integrations for a tenant within a single industry (TELCO / INSURANCE / ...).
     */
    public List<ClientIntegrationConfig> getIntegrationsByIndustry(String tenantId, String industry) {
        return integrationRepository.findByTenantIdAndIndustry(tenantId, industry);
    }

    /**
     * Get the active provider base URL for a tenant + integration type.
     * Returns null if not configured.
     */
    public String getBaseUrl(String tenantId, String integrationType) {
        return getIntegration(tenantId, integrationType)
                .filter(cfg -> "ACTIVE".equalsIgnoreCase(cfg.getStatus()))
                .map(ClientIntegrationConfig::getBaseUrl)
                .orElse(null);
    }

    /**
     * Get a credential value for a tenant + integration.
     */
    public String getCredential(String tenantId, String integrationType, String credentialKey) {
        return getIntegration(tenantId, integrationType)
                .filter(cfg -> "ACTIVE".equalsIgnoreCase(cfg.getStatus()))
                .map(cfg -> cfg.getCredential(credentialKey))
                .orElse(null);
    }

    /**
     * Invalidate cache for a specific tenant/integration.
     * Called by admin service when an integration is updated.
     */
    public void invalidateIntegration(String tenantId, String integrationType) {
        String cacheKey = INTEGRATION_CACHE_PREFIX + tenantId + ":" + integrationType;
        redisTemplate.delete(cacheKey);
        log.info("Integration cache invalidated: tenant={}, integration={}", tenantId, integrationType);
    }

    /**
     * Invalidate all integrations for a tenant.
     */
    public void invalidateAllForTenant(String tenantId) {
        // Simple: try common integration types across all industry packs
        for (String type : List.of(
                // Telco industry pack
                "DIALOG_MIFE", "DIALOG_BSS", "DIALOG_SMSC", "DIALOG_CATALOG",
                "HUTCH_BSS", "HUTCH_SMSC",
                "AIRTEL_GATEWAY", "AIRTEL_SMSC",
                // Insurance industry pack
                "AIA_INSURANCE", "AIA_PORTAL",
                "ALLIANZ_INSURANCE",
                // Cross-industry (payments, notifications, AI, etc.)
                "STRIPE", "ADYEN", "PAYPAL",
                "FIREBASE_PUSH", "TWILIO_SMS", "MESSAGEBIRD_SMS", "SENDGRID_EMAIL",
                "OPENAI", "ANTHROPIC", "GOOGLE_AI")) {
            invalidateIntegration(tenantId, type);
        }
    }
}
