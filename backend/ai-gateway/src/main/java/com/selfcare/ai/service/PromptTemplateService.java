package com.selfcare.ai.service;

import com.selfcare.platform.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Prompt Template Service — manages system prompt templates per tenant, industry, and use case.
 *
 * Templates support variable substitution with {{variable}} syntax.
 * Variables are resolved from tenant config, user profile, and runtime context.
 *
 * Built-in template IDs:
 *   default          — generic customer service (fallback)
 *   telco            — telecom-specific terminology (recharge, data pack, etc.)
 *   insurance        — insurance-specific terminology (policy, claim, premium, etc.)
 *   billing          — billing inquiry focus
 *   support          — complaint/issue focus
 *   sales           — upsell and recommendation focus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TEMPLATE_PREFIX = "selfcare:ai:prompt:";
    private static final Duration TEMPLATE_TTL = Duration.ofHours(24);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolve a prompt template for a tenant and use case.
     *
     * @param templateId template ID (e.g., "telco", "insurance", "billing")
     * @param tenantId tenant for variable resolution
     * @param variables runtime variables to substitute
     * @return the resolved system prompt
     */
    public String resolvePrompt(String templateId, String tenantId,
                               Map<String, String> variables) {
        String raw = getTemplate(templateId, tenantId);
        return substituteVariables(raw, variables);
    }

    /**
     * Get a template's raw content (with {{variable}} placeholders).
     */
    public String getTemplate(String templateId, String tenantId) {
        // 1. Try tenant-specific override
        String tenantKey = TEMPLATE_PREFIX + tenantId + ":" + templateId;
        Object cached = redisTemplate.opsForValue().get(tenantKey);
        if (cached instanceof String s && !s.isBlank()) {
            return s;
        }

        // 2. Try industry template
        String industry = resolveIndustry(tenantId);
        if (industry != null && !industry.equals(templateId)) {
            String industryKey = TEMPLATE_PREFIX + industry + ":" + templateId;
            cached = redisTemplate.opsForValue().get(industryKey);
            if (cached instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        // 3. Fall back to built-in defaults
        return getBuiltInTemplate(templateId);
    }

    /**
     * Save a template override for a tenant.
     */
    public void saveTemplate(String tenantId, String templateId, String content) {
        String key = TEMPLATE_PREFIX + tenantId + ":" + templateId;
        redisTemplate.opsForValue().set(key, content, TEMPLATE_TTL);
        log.info("Saved prompt template: tenant={}, template={}", tenantId, templateId);
    }

    /**
     * List all available template IDs for a tenant.
     */
    public List<String> listTemplateIds(String tenantId) {
        return List.of("default", "telco", "insurance", "billing", "support", "sales");
    }

    // -------------------------------------------------------------------------
    // Variable substitution
    // -------------------------------------------------------------------------

    /**
     * Substitute {{variable}} placeholders in the template.
     */
    public String substituteVariables(String template, Map<String, String> variables) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        // Remove any unsubstituted variables
        result = result.replaceAll("\\{\\{[^}]+\\}\\}", "");
        return result.trim();
    }

    // -------------------------------------------------------------------------
    // Industry resolution
    // -------------------------------------------------------------------------

    private String resolveIndustry(String tenantId) {
        // In production: consult TenantConfigurationService
        if (tenantId.startsWith("aia-")) return "insurance";
        if (tenantId.startsWith("dialog-") || tenantId.startsWith("hutch-") || tenantId.startsWith("airtel-")) {
            return "telco";
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Built-in default templates
    // -------------------------------------------------------------------------

    private String getBuiltInTemplate(String templateId) {
        return switch (templateId) {
            case "telco" -> """
                You are a helpful customer service assistant for a telecom operator.

                Guidelines:
                - Use clear, friendly language appropriate for a general audience
                - Keep responses concise — mobile users have limited screen space
                - Always confirm understanding before taking action
                - Escalate to a human agent when the issue is complex or sensitive
                - Never share internal system details or pricing formulas

                Telco terminology:
                - "recharge" (not "top-up" unless the customer uses it)
                - "data pack" (not "bundle" unless referring to a bundle offer)
                - "balance" (not "credit" for postpaid)
                - "plan" (not "tariff" unless customer uses it)

                If you need to check the customer's balance, usage, or plans, use the available tools.
                """;

            case "insurance" -> """
                You are a helpful insurance advisor assistant.

                Guidelines:
                - Use clear, empathetic language — insurance involves sensitive life events
                - Avoid jargon; explain technical terms
                - Be precise about policy numbers, dates, and amounts
                - Never give definitive medical or legal advice
                - Always direct customers to submit official claims through the app

                Insurance terminology:
                - "policy" (not "contract")
                - "premium" (not "payment")
                - "claim" (not "request" for insurance claims)
                - "beneficiary" (not "recipient")
                - "coverage" (not "limit" when referring to what is insured)
                """;

            case "billing" -> """
                You are a billing specialist assistant.

                Guidelines:
                - Be precise with amounts and dates
                - Explain billing cycle clearly (billing period, due date, late fees)
                - Offer payment options proactively
                - If a bill seems incorrect, acknowledge the concern and escalate
                - Always verify bill identity before discussing specific amounts
                """;

            case "support" -> """
                You are a support specialist assistant.

                Guidelines:
                - Show empathy first, especially for frustrated customers
                - Acknowledge the problem before jumping to solutions
                - Offer workarounds if a fix isn't immediately available
                - Create a support ticket for issues that need human follow-up
                - Thank the customer for their patience
                """;

            case "sales" -> """
                You are a helpful sales advisor.

                Guidelines:
                - Understand the customer's current usage before recommending
                - Explain value, not just features
                - Never pressure the customer — inform and let them decide
                - Highlight promotions or savings clearly
                - If the current plan is the best fit, say so honestly
                """;

            default -> """
                You are a helpful customer service assistant.

                Guidelines:
                - Be friendly, clear, and concise
                - Use the customer's language (match their tone — formal or casual)
                - Verify the customer's identity before discussing sensitive information
                - Use available tools to check balances, usage, and plans
                - When uncertain, say so and offer to connect to a human agent
                """;
        };
    }
}
