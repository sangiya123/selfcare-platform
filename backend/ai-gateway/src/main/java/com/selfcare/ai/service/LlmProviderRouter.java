package com.selfcare.ai.service;

import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Routes LLM chat requests to the appropriate provider based on tenant configuration.
 *
 * Resolution order for a provider:
 * 1. Tenant-specific config via TenantConfigurationService
 *    (key: "ai.provider" or "ai.providers.<tenant>")
 * 2. Default provider from application.yml (selfcare.ai.default-provider)
 * 3. Anthropic as the ultimate fallback
 *
 * Once a provider name is resolved, the router looks up the LlmProvider
 * implementation in its map and returns it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProviderRouter {

    private final Map<String, LlmProvider> providers;
    private final TenantConfigurationService tenantConfigurationService;

    @Value("${selfcare.ai.default-provider:anthropic}")
    private String defaultProvider;

    /**
     * Resolve the LLM provider for a given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the LLM provider implementation
     */
    public LlmProvider resolveProvider(String tenantId) {
        String providerName = switchProvider(tenantId);
        return resolveByName(providerName);
    }

    /**
     * Switch (resolve) the provider name for a tenant based on configuration.
     *
     * @param tenantId the tenant identifier
     * @return the provider name ("anthropic", "openai", "google-ai")
     */
    public String switchProvider(String tenantId) {
        // 1. Check tenant-specific config
        try {
            String configured = tenantConfigurationService.getIntegration(tenantId, "AI_PROVIDER")
                    .filter(cfg -> "ACTIVE".equalsIgnoreCase(cfg.getStatus()))
                    .map(cfg -> cfg.getMetadata() != null ? cfg.getMetadata().get("provider") : null)
                    .orElse(null);
            if (configured != null && !configured.isBlank()) {
                log.debug("Provider from tenant config: tenant={}, provider={}", tenantId, configured);
                return configured;
            }
        } catch (Exception e) {
            log.warn("Failed to read tenant config for provider: tenant={}, error={}", tenantId, e.getMessage());
        }

        // 2. Fall back to default
        log.debug("Using default provider: tenant={}, provider={}", tenantId, defaultProvider);
        return defaultProvider;
    }

    /**
     * Simple one-shot completion through the resolved provider for the tenant.
     *
     * @param prompt   the user prompt to send
     * @param model    the model to use
     * @param tenantId the tenant identifier
     * @param userId   the user identifier (optional)
     * @return the LLM text response, or null if the call fails
     */
    public String route(String prompt, String model, String tenantId, String userId) {
        LlmProvider provider = resolveProvider(tenantId);
        try {
            AIResponse response = provider.chat(ChatRequest.builder()
                    .messages(List.of(ChatRequest.Message.builder().role("user").content(prompt).build()))
                    .tenantId(tenantId)
                    .userId(userId)
                    .streaming(false)
                    .build());
            return response != null ? response.getContent() : null;
        } catch (Exception e) {
            log.warn("LLM route failed: tenant={}, model={}, error={}", tenantId, model, e.getMessage());
            return null;
        }
    }

    /**
     * Resolve a provider by its name.
     *
     * @param name the provider name
     * @return the LlmProvider implementation, or the default (anthropic) if not found
     */
    public LlmProvider resolveByName(String name) {
        // Spring autowires all LlmProvider beans with their bean name as key.
        // Convention: the bean name is the provider name + "LlmProvider",
        // but we strip the suffix to match the provider name.
        String beanName = name + "LlmProvider";
        LlmProvider provider = providers.get(beanName);
        if (provider != null) {
            return provider;
        }

        // Also try direct match
        provider = providers.get(name);
        if (provider != null) {
            return provider;
        }

        log.warn("Unknown LLM provider: {}, falling back to anthropic", name);
        LlmProvider fallback = providers.get("anthropicLlmProvider");
        if (fallback == null) {
            throw new IllegalStateException("No LLM provider available, including fallback");
        }
        return fallback;
    }
}
