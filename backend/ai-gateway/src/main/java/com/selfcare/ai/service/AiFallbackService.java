package com.selfcare.ai.service;

import com.selfcare.ai.service.AIResponse.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AI Fallback Service — provides canned responses when the LLM is unavailable.
 *
 * Strategy:
 *   1. Intent-based templates: pre-written responses for each Intent
 *   2. Quick-action tools: skip the LLM entirely for simple queries
 *   3. Local FAQ search: answer from indexed knowledge base
 *
 * Always available, no API keys required. Used as the final fallback in the
 * chat pipeline. The LLM is preferred when available — fallback only kicks in
 * when the LLM returns an error or after circuit-breaker opens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFallbackService {

    private final ToolExecutor toolExecutor;
    private final RAGService ragService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final SentimentAnalysisService sentimentService;

    @Value("${selfcare.ai.fallback.enabled:true}")
    private boolean fallbackEnabled = true;

    // Local answer templates per intent
    private static final Map<IntentClassification.Intent, List<String>> TEMPLATES = new HashMap<>();

    static {
        TEMPLATES.put(IntentClassification.Intent.BALANCE_INQUIRY, List.of(
                "Your current balance is shown on the home screen. You can also dial *123# to check.",
                "Your balance and remaining data are available on the main dashboard. For a quick check, use the mobile app or dial *123#."
        ));
        TEMPLATES.put(IntentClassification.Intent.RECHARGE_HELP, List.of(
                "To recharge, you can: 1) Buy a scratch card and enter the code, 2) Use the mobile app with a payment card, 3) Bank transfer, 4) Set up auto-recharge."
        ));
        TEMPLATES.put(IntentClassification.Intent.BILL_INQUIRY, List.of(
                "Your latest bill is on the Bills screen. You can view the breakdown and pay directly from the app."
        ));
        TEMPLATES.put(IntentClassification.Intent.USAGE_INQUIRY, List.of(
                "Your data, voice, and SMS usage are shown on the Usage screen. The progress bars indicate how much of your allowance remains."
        ));
        TEMPLATES.put(IntentClassification.Intent.PLAN_CHANGE, List.of(
                "Browse available plans on the Bundles screen, or ask me to recommend one based on your usage."
        ));
        TEMPLATES.put(IntentClassification.Intent.BUNDLE_PURCHASE, List.of(
                "You can browse and purchase data/voice bundles on the Bundles screen, or I can recommend one based on your usage pattern."
        ));
        TEMPLATES.put(IntentClassification.Intent.GREETING, List.of(
                "Hi! How can I help you today? You can ask me about your balance, plans, bills, or any account issue.",
                "Hello! What can I help you with today?"
        ));
        TEMPLATES.put(IntentClassification.Intent.COMPLAINT, List.of(
                "I'm sorry to hear you're having trouble. I can create a support ticket for you, or you can reach our support team directly on 123.",
                "I understand your frustration. Let me help — could you describe the issue in more detail? I can also create a support ticket if needed."
        ));
        TEMPLATES.put(IntentClassification.Intent.FAQ, List.of(
                "I can help with that. Could you provide more details? You can also check our FAQ in the Support section of the app."
        ));
        TEMPLATES.put(IntentClassification.Intent.GENERAL, List.of(
                "I'm here to help! You can ask me about your account, balance, plans, bills, or open a support ticket."
        ));
        TEMPLATES.put(IntentClassification.Intent.UNKNOWN, List.of(
                "I'm not sure I understood. Could you rephrase? You can also try one of the suggested topics on the home screen."
        ));
    }

    // Lightweight intent classifier for fallback
    private final FallbackIntentClassifier fallbackIntentClassifier = new FallbackIntentClassifier();

    /**
     * Generate a fallback response. Returns null if no suitable fallback exists.
     */
    public AIResponse respond(String message, String tenantId, String userId) {
        if (!fallbackEnabled) return null;

        log.info("AI fallback invoked: tenant={}, user={}", tenantId, userId);

        // 1. Classify the intent
        IntentClassification classification = fallbackIntentClassifier.classify(message);

        // 2. Try the appropriate tool first (if it can answer directly)
        if (classification.getIntent() == IntentClassification.Intent.BALANCE_INQUIRY) {
            // The LLM would call get_balance tool — do that directly
            try {
                AIResponse.ToolCallResult result = toolExecutor.execute(
                        "get_balance", "{}", tenantId, userId, null);
                if (result.isSuccess() && result.getResult() != null) {
                    return buildResponse(
                            "I checked your account. " + extractKeyValue(result.getResult()),
                            classification,
                            List.of(result)
                    );
                }
            } catch (Exception e) {
                log.debug("Fallback tool execution failed: {}", e.getMessage());
            }
        }

        // 3. Try a RAG search
        try {
            String rag = ragService.retrieve(message, tenantId, userId, 1);
            if (!rag.isBlank()) {
                return buildResponse(
                        extractTopAnswer(rag) + "\n\n(This is a quick answer — full AI is being prepared.)",
                        classification,
                        List.of()
                );
            }
        } catch (Exception ignored) {}

        // 4. Template response
        List<String> templates = TEMPLATES.getOrDefault(
                classification.getIntent(), TEMPLATES.get(IntentClassification.Intent.GENERAL));
        String content = templates.get(new Random().nextInt(templates.size()));

        return buildResponse(content, classification, List.of());
    }

    private AIResponse buildResponse(String content, IntentClassification classification,
                                    List<AIResponse.ToolCallResult> toolsUsed) {
        return AIResponse.builder()
                .content(content)
                .intent(classification)
                .toolsUsed(toolsUsed.isEmpty() ? null : toolsUsed)
                .provider("fallback")
                .model("template-v1")
                .tokenUsage(TokenUsage.builder()
                        .promptTokens(0)
                        .completionTokens(content.split("\\s+").length)
                        .totalTokens(content.split("\\s+").length)
                        .build())
                .build();
    }

    private String extractKeyValue(String result) {
        if (result == null) return "";
        // Try to extract a balance or amount from the JSON
        Pattern amount = Pattern.compile("\"balance\"\\s*:\\s*\"?([0-9.,]+)\"?");
        var matcher = amount.matcher(result);
        if (matcher.find()) {
            return "Balance: " + matcher.group(1);
        }
        return "Account information retrieved.";
    }

    private String extractTopAnswer(String rag) {
        // First Q/A pair
        int firstQEnd = rag.indexOf("\nA: ");
        if (firstQEnd > 0) {
            int answerStart = firstQEnd + 4;
            int nextQ = rag.indexOf("\nQ: ", answerStart);
            return rag.substring(answerStart, nextQ > 0 ? nextQ : rag.length()).trim();
        }
        return rag.substring(0, Math.min(200, rag.length())).trim();
    }

    // -------------------------------------------------------------------------
    // Lightweight intent classifier (mirrors AIModelGateway.classifyIntent)
    // -------------------------------------------------------------------------

    static class FallbackIntentClassifier {
        public IntentClassification classify(String message) {
            if (message == null || message.isBlank()) {
                return IntentClassification.unknown(0.0);
            }
            String lower = message.toLowerCase();
            IntentClassification.Intent intent;
            double confidence;
            String suggestedAction;

            if (lower.contains("balance") || lower.contains("how much") || lower.contains("remaining")) {
                intent = IntentClassification.Intent.BALANCE_INQUIRY;
                confidence = 0.9;
                suggestedAction = "check_balance";
            } else if (lower.contains("recharge") || lower.contains("top up") || lower.contains("topup")) {
                intent = IntentClassification.Intent.RECHARGE_HELP;
                confidence = 0.9;
                suggestedAction = "show_recharge_options";
            } else if (lower.contains("complaint") || lower.contains("issue") || lower.contains("problem")) {
                intent = IntentClassification.Intent.COMPLAINT;
                confidence = 0.85;
                suggestedAction = "create_support_ticket";
            } else if (lower.contains("bill") || lower.contains("invoice") || lower.contains("payment")) {
                intent = IntentClassification.Intent.BILL_INQUIRY;
                confidence = 0.85;
                suggestedAction = "show_bills";
            } else if (lower.contains("usage") || lower.contains("data left") || lower.contains("minutes left")) {
                intent = IntentClassification.Intent.USAGE_INQUIRY;
                confidence = 0.85;
                suggestedAction = "show_usage";
            } else if (lower.contains("plan") || lower.contains("package")) {
                intent = IntentClassification.Intent.PLAN_CHANGE;
                confidence = 0.8;
                suggestedAction = "show_plans";
            } else if (lower.contains("hi") || lower.contains("hello") || lower.contains("hey")) {
                intent = IntentClassification.Intent.GREETING;
                confidence = 0.95;
                suggestedAction = "greet";
            } else {
                intent = IntentClassification.Intent.GENERAL;
                confidence = 0.5;
                suggestedAction = "general_assist";
            }
            return IntentClassification.builder()
                    .intent(intent)
                    .confidence(confidence)
                    .suggestedAction(suggestedAction)
                    .autoActEligible(confidence >= 0.8)
                    .build();
        }
    }
}
