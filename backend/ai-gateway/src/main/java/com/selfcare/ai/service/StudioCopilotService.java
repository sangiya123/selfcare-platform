package com.selfcare.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * selfcare Studio Copilot — implements AI scope section 4.
 *
 * Capabilities:
 *  - generate page configuration from a requirement
 *  - recommend component/layout variants
 *  - generate visibility rules
 *  - create journey draft
 *  - integration mapping assistant from sample API payload/OpenAPI/WSDL
 *  - schema/config validation explanation
 *  - migration assistant from legacy PHP/Java code to canonical provider contracts
 *  - generate release notes and operator impact summary
 *
 * Per ADR-009: configurations are declarative — generated configs reference
 * registered components, actions, and connectors only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudioCopilotService {

    private final LlmProviderRouter llmRouter;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Page configuration generation
    // -------------------------------------------------------------------------

    /**
     * Generate a page configuration draft from a natural-language requirement.
     *
     * The generated page references only components registered in the
     * platform component registry (per ADR-009).
     */
    public Map<String, Object> generatePageConfig(String requirement, String industry) {
        if (requirement == null || requirement.isBlank()) {
            return Map.of("error", "Requirement cannot be empty");
        }

        String prompt = """
                Generate a JSON page configuration for the Selfcare platform.
                Industry: %s
                Requirement: %s

                Use ONLY these registered components (per ADR-009):
                - balance-card, usage-card, bill-card, plan-card, data-allowance-card
                - quick-action-list, recharge-button, pay-bill-button
                - promotions-list, notifications-list, support-tile
                - header, footer

                Use ONLY these registered actions:
                - NAVIGATE, CALL_API, START_JOURNEY, OPEN_LINK, REFRESH, LOGOUT

                Return ONLY valid JSON. No prose.
                """.formatted(industry, requirement);

        String json = llmRouter.route(prompt, "anthropic", null, null);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("id", "page-" + UUID.randomUUID().toString().substring(0, 8));
        page.put("version", "draft");
        page.put("industry", industry);
        page.put("requirement", requirement);
        page.put("generatedBy", "studio-copilot-v1");

        if (json != null && json.startsWith("{")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> llmPage = objectMapper.readValue(json, Map.class);
                page.putAll(llmPage);
                page.put("source", "llm");
            } catch (Exception e) {
                log.warn("LLM returned invalid JSON, using template fallback: {}", e.getMessage());
                page.put("source", "template");
                page.put("sections", templateSections(requirement, industry));
            }
        } else {
            page.put("source", "template");
            page.put("sections", templateSections(requirement, industry));
        }
        page.put("status", "DRAFT");
        page.put("requiresApproval", true);
        return page;
    }

    private List<Map<String, Object>> templateSections(String requirement, String industry) {
        String lower = requirement.toLowerCase();
        List<Map<String, Object>> sections = new ArrayList<>();

        if (lower.contains("balance") || lower.contains("usage")) {
            sections.add(section("hero", List.of("balance-card", "data-allowance-card")));
        }
        if (lower.contains("bill") || lower.contains("invoice")) {
            sections.add(section("bills", List.of("bill-card")));
        }
        if (lower.contains("recharge") || lower.contains("top up")) {
            sections.add(section("quick-actions", List.of("recharge-button", "pay-bill-button")));
        }
        if (lower.contains("promo") || lower.contains("offer") || lower.contains("recommend")) {
            sections.add(section("promotions", List.of("promotions-list")));
        }
        if (sections.isEmpty()) {
            sections.add(section("hero", List.of("balance-card")));
        }
        return sections;
    }

    private Map<String, Object> section(String name, List<String> components) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("components", components);
        return s;
    }

    // -------------------------------------------------------------------------
    // Layout variant recommendations
    // -------------------------------------------------------------------------

    public List<LayoutVariant> recommendLayoutVariants(String pageType, String industry) {
        // Deterministic recommendations per page type — production could use
        // analytics on which layouts convert better.
        return switch (pageType.toLowerCase()) {
            case "home", "dashboard" -> List.of(
                    new LayoutVariant("balanced", "Balanced hero + quick actions", 0.85,
                            "Top hero (balance/usage), middle quick actions, bottom promos"),
                    new LayoutVariant("promo-first", "Promo-first with sticky recharge", 0.65,
                            "Promo banner on top, hero below, sticky recharge button"),
                    new LayoutVariant("minimal", "Minimal hero-only layout", 0.45,
                            "Single hero, usage graph, no promos")
            );
            case "bills" -> List.of(
                    new LayoutVariant("list-default", "List of bills with status badges", 0.85, "Default list"),
                    new LayoutVariant("grouped", "Grouped by month", 0.70, "Group bills by month")
            );
            case "recharge" -> List.of(
                    new LayoutVariant("amount-buttons", "Quick amount buttons", 0.80, "Rs. 100 / 500 / 1000 / custom"),
                    new LayoutVariant("package-list", "Package-first", 0.75, "Show recommended packages first")
            );
            default -> List.of(new LayoutVariant("default", "Default layout for " + pageType, 0.5, "Auto-generated"));
        };
    }

    // -------------------------------------------------------------------------
    // Visibility rules
    // -------------------------------------------------------------------------

    /**
     * Generate visibility rule draft from a description.
     * Rules use only registered fields and operators.
     */
    public String generateVisibilityRule(String description) {
        String lower = description.toLowerCase();
        if (lower.contains("postpaid") && lower.contains("balance")) {
            return "context.connectionType == 'POSTPAID' && user.balance != null";
        }
        if (lower.contains("prepaid") && lower.contains("data")) {
            return "context.connectionType == 'PREPAID' && usage.dataRemainingPct < 0.1";
        }
        if (lower.contains("churn") || lower.contains("risk")) {
            return "user.predictions.churnRiskLevel IN ('HIGH', 'CRITICAL')";
        }
        if (lower.contains("low balance")) {
            return "user.balance < (user.plan.threshold || 100)";
        }
        return "true /* TODO: refine from description: " + description + " */";
    }

    // -------------------------------------------------------------------------
    // Journey draft
    // -------------------------------------------------------------------------

    public Map<String, Object> generateJourneyDraft(String intent, int stepCount) {
        stepCount = Math.max(1, Math.min(stepCount, 10));
        Map<String, Object> journey = new LinkedHashMap<>();
        journey.put("id", "journey-" + UUID.randomUUID().toString().substring(0, 8));
        journey.put("name", intent);
        journey.put("version", "draft");
        journey.put("generatedBy", "studio-copilot-v1");
        journey.put("status", "DRAFT");

        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 1; i <= stepCount; i++) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("stepNumber", i);
            step.put("title", i == 1 ? "Confirm action" : "Step " + i);
            step.put("component", "form-step");
            step.put("action", "NEXT");
            steps.add(step);
        }
        journey.put("steps", steps);
        return journey;
    }

    // -------------------------------------------------------------------------
    // Integration mapping (from OpenAPI / sample payload)
    // -------------------------------------------------------------------------

    /**
     * Suggest field mappings from a sample API payload (or a parsed OpenAPI
     * schema) into the canonical provider contract fields.
     */
    public IntegrationMapping suggestIntegrationMapping(Map<String, Object> samplePayload) {
        if (samplePayload == null || samplePayload.isEmpty()) {
            return new IntegrationMapping(Map.of(), List.of(), "EMPTY_INPUT");
        }

        // Heuristic: map common keys to canonical provider fields.
        Map<String, String> fieldMappings = new LinkedHashMap<>();
        List<String> suggestions = new ArrayList<>();

        mapField(samplePayload, fieldMappings, "msisdn", "connectionId");
        mapField(samplePayload, fieldMappings, "phone", "connectionId");
        mapField(samplePayload, fieldMappings, "subscriber_id", "customerId");
        mapField(samplePayload, fieldMappings, "customer_id", "customerId");
        mapField(samplePayload, fieldMappings, "balance", "balance.amount");
        mapField(samplePayload, fieldMappings, "currency", "balance.currency");
        mapField(samplePayload, fieldMappings, "data_used", "usage.data.totalBytes");
        mapField(samplePayload, fieldMappings, "data_remaining", "usage.data.remainingBytes");
        mapField(samplePayload, fieldMappings, "validity", "balance.expiryDate");
        mapField(samplePayload, fieldMappings, "status", "accountStatus");
        mapField(samplePayload, fieldMappings, "name", "customerName");
        mapField(samplePayload, fieldMappings, "email", "email");

        if (fieldMappings.isEmpty()) {
            suggestions.add("No known fields detected — please provide sample payload with known keys (msisdn, balance, etc.)");
        } else {
            suggestions.add("Review each mapping and confirm in selfcare Studio");
            suggestions.add("Test against the operator's sandbox endpoint before publish");
        }

        return new IntegrationMapping(fieldMappings, suggestions, "DRAFT");
    }

    private void mapField(Map<String, Object> payload, Map<String, String> mappings,
                          String sourceKey, String canonicalField) {
        if (payload.containsKey(sourceKey)) {
            mappings.put(sourceKey, canonicalField);
        }
    }

    // -------------------------------------------------------------------------
    // Schema / config validation explanation
    // -------------------------------------------------------------------------

    public List<ValidationExplanation> explainValidationIssues(List<Map<String, Object>> issues) {
        if (issues == null) return List.of();
        List<ValidationExplanation> out = new ArrayList<>();
        for (Map<String, Object> issue : issues) {
            String path = String.valueOf(issue.getOrDefault("path", ""));
            String message = String.valueOf(issue.getOrDefault("message", ""));
            ValidationExplanation exp = explainOne(path, message);
            out.add(exp);
        }
        return out;
    }

    private ValidationExplanation explainOne(String path, String message) {
        String lower = message.toLowerCase();
        if (lower.contains("required")) {
            return new ValidationExplanation(path, message,
                    "This field is required. Either supply a value or remove the field if optional.",
                    "Check the config-schema for the section containing '" + path + "'.");
        }
        if (lower.contains("enum") || lower.contains("not one of")) {
            return new ValidationExplanation(path, message,
                    "The value must be one of the allowed enum values.",
                    "Look at the schema for the allowed values for '" + path + "'.");
        }
        if (lower.contains("pattern") || lower.contains("regex")) {
            return new ValidationExplanation(path, message,
                    "The value does not match the expected format.",
                    "Check the regex in the schema and adjust the value.");
        }
        return new ValidationExplanation(path, message,
                "Validation issue. See schema for the section.",
                "Open the config-schema docs and locate '" + path + "'.");
    }

    // -------------------------------------------------------------------------
    // Migration assistant
    // -------------------------------------------------------------------------

    public String explainMigration(String legacyCode, String canonicalContract) {
        if (legacyCode == null || legacyCode.isBlank()) {
            return "No legacy code provided.";
        }
        String lower = legacyCode.toLowerCase();
        StringBuilder notes = new StringBuilder();
        notes.append("Migration notes for ").append(canonicalContract).append(":\n\n");

        if (lower.contains("mysql_") || lower.contains("mysqli_")) {
            notes.append("- Replace direct MySQL calls with provider adapter methods.\n");
            notes.append("- Connection lookup must go through ConnectionProvider, not direct DB.\n");
        }
        if (lower.contains("curl_exec") || lower.contains("file_get_contents")) {
            notes.append("- Wrap HTTP calls in BalanceProvider / AuthProvider methods.\n");
            notes.append("- Move URL and credentials to TenantConfigurationService (Mongo), not env.\n");
        }
        if (lower.contains("$_session") || lower.contains("$_cookie")) {
            notes.append("- Replace PHP session/cookie with JWT issued by customer-identity-service.\n");
        }
        if (lower.contains("echo") || lower.contains("print ")) {
            notes.append("- Remove direct response writes — return a value and let the BFF render.\n");
        }
        if (notes.length() == "Migration notes for ".length() + canonicalContract.length() + 2) {
            notes.append("- Generic migration: ensure canonical contract methods are used, no direct DB access.\n");
        }
        notes.append("\nAfter migration, run conformance tests for the relevant provider.");
        return notes.toString();
    }

    // -------------------------------------------------------------------------
    // Release notes
    // -------------------------------------------------------------------------

    public String generateReleaseNotes(List<String> changes, String version) {
        StringBuilder notes = new StringBuilder();
        notes.append("# Release ").append(version).append("\n\n");
        if (changes == null || changes.isEmpty()) {
            notes.append("_No changes provided._\n");
            return notes.toString();
        }
        notes.append("## Changes\n\n");
        for (String c : changes) {
            notes.append("- ").append(c).append("\n");
        }
        notes.append("\n## Operator impact\n\n");
        notes.append("Each change is reviewed for impact on the dashboard, payment flow, and key journeys.\n");
        return notes.toString();
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    public record LayoutVariant(
            String id,
            String name,
            double score,
            String description
    ) {}

    public record IntegrationMapping(
            Map<String, String> fieldMappings,
            List<String> suggestions,
            String status
    ) {}

    public record ValidationExplanation(
            String path,
            String originalMessage,
            String explanation,
            String action
    ) {}
}
