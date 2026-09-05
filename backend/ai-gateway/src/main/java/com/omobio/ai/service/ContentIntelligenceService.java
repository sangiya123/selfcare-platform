package com.omobio.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Content Intelligence service — implements AI scope section 3:
 *
 *  - generate/rewrite operator content
 *  - translate/localize with terminology memory
 *  - image/banner copy suggestions
 *  - accessibility checks/alt text suggestion
 *  - campaign variants
 *  - FAQ generation from approved knowledge
 *
 * Human approval is required before production publication unless a
 * low-risk policy explicitly allows automation.
 *
 * v1 uses template-based generation that respects industry-specific
 * terminology memory. The same surface accepts LLM-backed implementations
 * by swapping the underlying generator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIntelligenceService {

    private final LlmProviderRouter llmRouter;

    // -------------------------------------------------------------------------
    // Terminology memory
    // -------------------------------------------------------------------------

    /**
     * Industry- and locale-specific term mappings.
     * Production: stored in MongoDB and editable from Selfcare Studio.
     */
    private static final Map<String, Map<String, String>> TERMINOLOGY = new HashMap<>();

    static {
        // Telco (English)
        Map<String, String> telcoEn = new HashMap<>();
        telcoEn.put("customer", "subscriber");
        telcoEn.put("plan", "package");
        telcoEn.put("data", "mobile data");
        telcoEn.put("top up", "recharge");
        telcoEn.put("phone", "mobile number");
        TERMINOLOGY.put("telco-en", telcoEn);

        // Insurance (English)
        Map<String, String> insuranceEn = new HashMap<>();
        insuranceEn.put("plan", "policy");
        insuranceEn.put("top up", "top-up premium");
        insuranceEn.put("customer", "policyholder");
        insuranceEn.put("package", "coverage");
        TERMINOLOGY.put("insurance-en", insuranceEn);

        // Sinhala
        Map<String, String> sinhala = new HashMap<>();
        sinhala.put("recharge", "රිචාජ්");
        sinhala.put("balance", "ශේෂය");
        sinhala.put("package", "පැකේජය");
        TERMINOLOGY.put("si", sinhala);

        // Tamil
        Map<String, String> tamil = new HashMap<>();
        tamil.put("recharge", "ரீசார்ஜ்");
        tamil.put("balance", "இருப்பு");
        tamil.put("package", "தொகுப்பு");
        TERMINOLOGY.put("ta", tamil);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Rewrite operator-provided content using the AI gateway (uses the
     * configured LLM provider). Falls back to template rewrite if LLM
     * is unavailable.
     */
    public ContentResult rewrite(String content, String tenantId, String userId) {
        if (content == null || content.isBlank()) {
            return new ContentResult(content, List.of(), "no-op", "EMPTY_INPUT");
        }
        String prompt = "Rewrite the following selfcare content to be clearer, more concise, " +
                "and consistent with the operator's tone. Preserve all factual claims and numbers.\n\n" +
                "CONTENT:\n" + content;
        String rewritten = llmRouter.route(prompt, "anthropic", tenantId, userId);
        if (rewritten == null || rewritten.isBlank() || rewritten.equals(content)) {
            rewritten = templateRewrite(content);
        }
        return new ContentResult(rewritten, List.of(), "rewrite", "SUCCESS");
    }

    /**
     * Translate content to a target locale using the LLM, then apply
     * terminology memory substitutions.
     */
    public ContentResult translate(String content, String targetLocale,
                                   String industry, String tenantId, String userId) {
        if (content == null || content.isBlank()) {
            return new ContentResult(content, List.of(), "no-op", "EMPTY_INPUT");
        }
        String prompt = "Translate the following selfcare content to locale " + targetLocale +
                ". Preserve numbers, currency, technical identifiers, and product names.\n\n" +
                "CONTENT:\n" + content;
        String translated = llmRouter.route(prompt, "anthropic", tenantId, userId);
        if (translated == null || translated.isBlank()) {
            translated = content; // fallback: don't translate
        }
        translated = applyTerminology(translated, industry, targetLocale);
        return new ContentResult(translated, List.of(), "translate", "SUCCESS");
    }

    /**
     * Suggest accessibility improvements and alt text for an image
     * described by `imageDescription`.
     */
    public AccessibilityReport accessibilityCheck(String imageDescription,
                                                  String surroundingContext,
                                                  String industry) {
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        if (imageDescription == null || imageDescription.isBlank()) {
            issues.add("MISSING_ALT_TEXT");
            suggestions.add("Alt text is required for non-decorative images.");
        } else if (imageDescription.length() < 10) {
            issues.add("ALT_TOO_SHORT");
            suggestions.add("Alt text should describe the image's purpose in the context, not just the visual content.");
        }
        if (imageDescription != null && imageDescription.length() > 200) {
            issues.add("ALT_TOO_LONG");
            suggestions.add("Alt text longer than ~125 characters is hard to read — move details to the surrounding text.");
        }
        if (surroundingContext != null && surroundingContext.toLowerCase().contains("click here")) {
            issues.add("AMBIGUOUS_LINK_TEXT");
            suggestions.add("Replace 'click here' with the destination or action: e.g. 'view your November bill'.");
        }
        if (surroundingContext != null && surroundingContext.toLowerCase().contains("see above")) {
            issues.add("SPATIAL_REFERENCE");
            suggestions.add("Avoid 'see above' / 'see below' — these don't work for screen readers.");
        }

        String suggestedAlt = generateAltText(imageDescription, industry);
        return new AccessibilityReport(
                issues.isEmpty() ? "PASS" : "ISSUES_FOUND",
                issues,
                suggestions,
                suggestedAlt);
    }

    /**
     * Generate campaign copy variants for a promotion.
     */
    public List<String> campaignVariants(String productName, String benefit,
                                         String industry, int count) {
        count = Math.max(1, Math.min(count, 8));
        List<String> templates = industry.equalsIgnoreCase("INSURANCE")
                ? List.of(
                    "Protect what matters — %s with %s. Apply in minutes.",
                    "Your %s deserves %s. Get covered today.",
                    "Insurance made simple: %s for %s.",
                    "Peace of mind starts with %s. %s included.")
                : List.of(
                    "Stay connected with %s — %s included.",
                    "Your perfect plan: %s with %s.",
                    "More for less: %s — %s.",
                    "Recharge smarter with %s. %s on every top-up.");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String tmpl = templates.get(i % templates.size());
            out.add(String.format(tmpl, productName, benefit));
        }
        return out;
    }

    /**
     * Generate FAQ questions/answers from a knowledge base entry.
     */
    public List<FaqItem> generateFaq(String knowledgeContent, int count) {
        if (knowledgeContent == null || knowledgeContent.isBlank()) {
            return List.of();
        }
        count = Math.max(1, Math.min(count, 10));

        // Split into sentences and select the most informative ones
        String[] sentences = knowledgeContent.split("(?<=[.!?])\\s+");
        List<String> useful = Arrays.stream(sentences)
                .filter(s -> s.length() > 30 && s.length() < 200)
                .limit(count)
                .collect(Collectors.toList());

        List<FaqItem> faqs = new ArrayList<>();
        for (int i = 0; i < useful.size(); i++) {
            String sentence = useful.get(i);
            // Generate a question based on the leading noun phrase (simplified)
            String question = deriveQuestion(sentence);
            faqs.add(new FaqItem(question, sentence, i + 1));
        }
        return faqs;
    }

    /**
     * Suggest an alt text caption for an image based on its description
     * and industry context.
     */
    public String suggestAltText(String imageDescription, String industry) {
        return generateAltText(imageDescription, industry);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String applyTerminology(String text, String industry, String locale) {
        // Build a lookup key
        String key = industry.toLowerCase() + "-" + (locale.startsWith("en") ? "en" : locale.toLowerCase());
        Map<String, String> terms = TERMINOLOGY.get(key);
        if (terms == null) terms = TERMINOLOGY.get(locale.toLowerCase());
        if (terms == null) return text;

        String out = text;
        for (Map.Entry<String, String> e : terms.entrySet()) {
            out = Pattern.compile("(?i)\\b" + Pattern.quote(e.getKey()) + "\\b")
                    .matcher(out)
                    .replaceAll(e.getValue());
        }
        return out;
    }

    private String templateRewrite(String content) {
        // Basic rewrite: trim, sentence-case the first word, normalize whitespace.
        String trimmed = content.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) return content;
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private String generateAltText(String description, String industry) {
        if (description == null || description.isBlank()) {
            return industry.equalsIgnoreCase("INSURANCE")
                    ? "Illustration of insurance coverage"
                    : "Illustration of mobile plan benefits";
        }
        return description;
    }

    private String deriveQuestion(String statement) {
        String s = statement.trim();
        if (s.toLowerCase().startsWith("to ")) {
            return "How do I " + s.substring(3) + "?";
        }
        if (s.toLowerCase().startsWith("you can ")) {
            return "What can I do — " + s.substring(8) + "?";
        }
        // Default: replace first period with a question mark
        return s.replaceFirst("\\.", "?");
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    public record ContentResult(
            String content,
            List<String> warnings,
            String action,
            String status
    ) {}

    public record AccessibilityReport(
            String status,
            List<String> issues,
            List<String> suggestions,
            String suggestedAlt
    ) {}

    public record FaqItem(
            String question,
            String answer,
            int order
    ) {}
}
