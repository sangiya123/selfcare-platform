package com.selfcare.ai.service;

import com.selfcare.platform.common.tenant.TenantConfigurationService;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * AI Model Gateway — main entry point for AI chat operations.
 *
 * Provides:
 * - chat(): sends a chat request to the appropriate LLM provider (Anthropic by default).
 * - classifyIntent(): lightweight intent classification without LLM call.
 * - getAvailableTools(): lists tools allowed for a tenant + scope.
 * - switchProvider(): resolves which LLM provider to use for a tenant.
 *
 * Provider routing is done via LlmProviderRouter.
 *
 * @see LlmProvider
 * @see LlmProviderRouter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIModelGateway {

    private final LlmProviderRouter llmProviderRouter;
    private final ToolPermissionService toolPermissionService;
    private final TenantConfigurationService tenantConfigurationService;
    private final RAGService ragService;
    private final RecommendationService recommendationService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final PromptTemplateService promptTemplateService;
    private final ContentModerationService moderationService;
    private final TokenUsageService tokenUsageService;
    private final AiFallbackService fallbackService;
    private final AiGovernanceService governanceService;
    private final PiiMaskingService piiMaskingService;

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------

    /**
     * Send a chat request to the appropriate LLM provider.
     *
     * Resolution order:
     * 1. tenantId in request overrides TenantContext
     * 2. Tenant configuration is consulted for provider selection
     * 3. Default provider (anthropic) is used as fallback
     *
     * @param request the chat request
     * @return the AI response
     */
    @CircuitBreaker(name = "ai.gateway.chat", fallbackMethod = "chatFallback")
    @TimeLimiter(name = "ai.gateway.chat")
    @Retry(name = "ai.gateway.chat")
    public AIResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();

        // Resolve tenant and user from request or context
        String tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContext.get().getTenantId();
        String userId = request.getUserId() != null ? request.getUserId() : TenantContext.get().getUserId();
        request.setTenantId(tenantId);
        request.setUserId(userId);

        // Rate limit checks
        if (!tokenUsageService.checkTenantRateLimit(tenantId)) {
            return AIResponse.builder()
                    .content("Our AI assistant is experiencing high demand right now. Please try again in a moment.")
                    .sessionId(request.getSessionId())
                    .build();
        }
        if (userId != null && !tokenUsageService.checkUserRateLimit(tenantId, userId)) {
            tokenUsageService.recordRateLimitViolation(tenantId, userId, "user_rpm");
            return AIResponse.builder()
                    .content("You're sending messages too quickly. Please wait a moment before trying again.")
                    .sessionId(request.getSessionId())
                    .build();
        }

        // Governance: enforce use-case policy (kill switch, provider allow list, budget)
        com.selfcare.ai.domain.AiUseCase useCase;
        try {
            useCase = governanceService.enforcePolicy("customer_chat", null);
        } catch (IllegalStateException e) {
            log.warn("Governance rejected chat: {}", e.getMessage());
            return AIResponse.builder()
                    .content("This service is temporarily unavailable. Please try again later.")
                    .sessionId(request.getSessionId())
                    .build();
        }

        // PII mask on user input (per use case policy)
        if (Boolean.TRUE.equals(useCase.getPiiMaskInput())) {
            for (ChatRequest.Message m : request.getMessages()) {
                if (m != null && m.getContent() != null && "user".equals(m.getRole())) {
                    m.setContent(piiMaskingService.mask(m.getContent()));
                }
            }
        }

        // Resolve the provider
        LlmProvider provider = llmProviderRouter.resolveProvider(tenantId);
        log.info("Chat request: tenant={}, user={}, provider={}, streaming={}, tools={}, messages={}",
                tenantId, userId, provider.getProviderName(),
                request.isStreaming(),
                request.getTools() != null ? request.getTools().size() : 0,
                request.getMessages() != null ? request.getMessages().size() : 0);

        // Augment with RAG + template + moderation
        if (request.getSystemPrompt() == null || request.getSystemPrompt().isBlank()) {
            String latestUserMessage = findLatestUserMessage(request.getMessages());
            if (latestUserMessage != null && !latestUserMessage.isBlank()) {
                // Moderation check on input
                ContentModerationService.ModerationResult modResult =
                        moderationService.moderateInput(latestUserMessage, tenantId, userId);
                if (!modResult.isSafe()) {
                    return AIResponse.builder()
                            .content("I can't help with that. " +
                                    (modResult.getReason() != null ? "(" + modResult.getReason() + ")" : ""))
                            .sessionId(request.getSessionId())
                            .build();
                }

                // Resolve prompt template
                String industry = resolveIndustry(tenantId);
                String templateId = request.getExtraParams() != null &&
                        request.getExtraParams().get("templateId") instanceof String s ? s : industry;
                Map<String, String> variables = new HashMap<>();
                variables.put("tenantName", tenantId);

                String systemPrompt = promptTemplateService.resolvePrompt(templateId, tenantId, variables);

                // Augment with RAG context
                String context = ragService.retrieve(latestUserMessage, tenantId, userId, 5);
                if (!context.isBlank()) {
                    systemPrompt = systemPrompt + "\n\n" + context;
                }

                // Add vector similarity results
                List<VectorEmbeddingService.SimilarChunk> similar =
                        vectorEmbeddingService.findSimilar(latestUserMessage, tenantId, 3);
                if (!similar.isEmpty()) {
                    StringBuilder semanticContext = new StringBuilder("\n\nRelevant information:\n");
                    for (VectorEmbeddingService.SimilarChunk chunk : similar) {
                        semanticContext.append("- ").append(chunk.text()).append("\n");
                    }
                    systemPrompt = systemPrompt + semanticContext;
                }

                request.setSystemPrompt(systemPrompt);
            }
        }

        // Attach tool definitions from permission service
        if (request.getTools() == null || request.getTools().isEmpty()) {
            List<ToolPermissionService.ToolDefinition> allowedTools =
                    toolPermissionService.getAllowedTools(tenantId, userId);
            request.setTools(allowedTools.stream()
                    .map(t -> ChatRequest.ToolDefinition.builder()
                            .name(t.getName())
                            .description(t.getDescription())
                            .inputSchema(t.getInputSchema())
                            .build())
                    .toList());
        }

        // Send to provider
        AIResponse response = provider.chat(request);
        response.setSessionId(request.getSessionId());
        response.setLatencyMs(System.currentTimeMillis() - start);

        // Record usage
        if (response.getTokenUsage() != null) {
            tokenUsageService.recordUsage(tenantId, userId, response.getModel(), response.getTokenUsage());
        }

        // Output moderation
        if (response.getContent() != null) {
            ContentModerationService.ModerationResult outMod =
                    moderationService.moderateOutput(response.getContent(), tenantId);
            if (!outMod.isSafe()) {
                response.setContent("[This response was filtered for safety reasons.]");
            }
        }

        // PII mask on output (per use case policy)
        if (Boolean.TRUE.equals(useCase.getPiiMaskOutput()) && response.getContent() != null) {
            response.setContent(piiMaskingService.mask(response.getContent()));
        }

        return response;
    }

    /**
     * Circuit breaker fallback for chat — returns a friendly error response.
     */
    @SuppressWarnings("unused")
    private AIResponse chatFallback(ChatRequest request, Throwable t) {
        log.error("Chat fallback triggered: tenant={}, error={}",
                request.getTenantId(), t.getMessage());
        // Try the smart fallback service first
        String tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContext.get().getTenantId();
        String userId = request.getUserId() != null ? request.getUserId() : TenantContext.get().getUserId();
        try {
            String latestUserMessage = findLatestUserMessage(request.getMessages());
            if (latestUserMessage != null) {
                AIResponse smart = fallbackService.respond(latestUserMessage, tenantId, userId);
                if (smart != null) {
                    smart.setSessionId(request.getSessionId());
                    return smart;
                }
            }
        } catch (Exception e) {
            log.warn("Smart fallback also failed: {}", e.getMessage());
        }
        return AIResponse.builder()
                .content("I'm having trouble connecting right now. Please try again in a moment, or contact support directly if this is urgent.")
                .sessionId(request.getSessionId())
                .build();
    }

    // -------------------------------------------------------------------------
    // Intent classification
    // -------------------------------------------------------------------------

    /**
     * Classify the intent of a user message.
     *
     * This is a lightweight, rule-based + keyword-based classification.
     * No LLM call is made — the goal is to provide fast intent signals for routing.
     *
     * @param message  the user's message
     * @param tenantId the tenant ID
     * @return the intent classification
     */
    public IntentClassification classifyIntent(String message, String tenantId) {
        if (message == null || message.isBlank()) {
            return IntentClassification.unknown(0.0);
        }

        String lower = message.toLowerCase();
        Map<String, String> entities = new HashMap<>();
        String suggestedAction;
        IntentClassification.Intent intent;
        double confidence;

        // BALANCE_INQUIRY
        if (matches(lower, "balance", "how much", "remaining", "credit left", "main balance", "top up balance", "check balance")) {
            intent = IntentClassification.Intent.BALANCE_INQUIRY;
            confidence = 0.9;
            suggestedAction = "check_balance";
        }
        // RECHARGE_HELP
        else if (matches(lower, "recharge", "top up", "topup", "add money", "load", "refill", "top-up")) {
            intent = IntentClassification.Intent.RECHARGE_HELP;
            confidence = 0.9;
            suggestedAction = "show_recharge_options";
        }
        // COMPLAINT
        else if (matches(lower, "complaint", "complain", "issue", "problem", "not working", "broken", "disappointed", "frustrated", "angry", "bad service", "poor service")) {
            intent = IntentClassification.Intent.COMPLAINT;
            confidence = 0.85;
            suggestedAction = "create_support_ticket";
        }
        // BILL_INQUIRY
        else if (matches(lower, "bill", "invoice", "statement", "payment due", "pay my", "outstanding", "amount due")) {
            intent = IntentClassification.Intent.BILL_INQUIRY;
            confidence = 0.85;
            suggestedAction = "show_bills";
        }
        // USAGE_INQUIRY
        else if (matches(lower, "usage", "how much data", "how much have i used", "remaining data", "data left", "minutes left", "data balance")) {
            intent = IntentClassification.Intent.USAGE_INQUIRY;
            confidence = 0.85;
            suggestedAction = "show_usage";
        }
        // PLAN_CHANGE
        else if (matches(lower, "change plan", "switch plan", "new plan", "upgrade plan", "downgrade", "change my plan")) {
            intent = IntentClassification.Intent.PLAN_CHANGE;
            confidence = 0.85;
            suggestedAction = "show_plans";
        }
        // BUNDLE_PURCHASE
        else if (matches(lower, "buy", "purchase", "add bundle", "activate", "subscribe", "data pack", "voice pack")) {
            intent = IntentClassification.Intent.BUNDLE_PURCHASE;
            confidence = 0.8;
            suggestedAction = "show_bundles";
        }
        // GREETING
        else if (matches(lower, "hi", "hello", "hey", "good morning", "good afternoon", "good evening", "howdy")) {
            intent = IntentClassification.Intent.GREETING;
            confidence = 0.95;
            suggestedAction = "greet";
        }
        // FAQ
        else if (matches(lower, "how do i", "how to", "what is", "where can", "can i", "is it possible", "tell me about", "explain")) {
            intent = IntentClassification.Intent.FAQ;
            confidence = 0.7;
            suggestedAction = "answer_faq";
        }
        // General / unknown
        else {
            intent = IntentClassification.Intent.GENERAL;
            confidence = 0.5;
            suggestedAction = "general_assist";
        }

        return IntentClassification.builder()
                .intent(intent)
                .confidence(confidence)
                .suggestedAction(suggestedAction)
                .extractedEntities(entities)
                .autoActEligible(confidence >= 0.8)
                .build();
    }

    // -------------------------------------------------------------------------
    // Tools
    // -------------------------------------------------------------------------

    /**
     * Get the list of tools available to a user in a given scope.
     *
     * @param tenantId the tenant
     * @param scope    the scope ("user", "tenant", "global")
     * @return list of available tool definitions
     */
    public List<ToolPermissionService.ToolDefinition> getAvailableTools(String tenantId, String scope) {
        String userId = TenantContext.get().getUserId();
        if ("tenant".equalsIgnoreCase(scope) || userId == null) {
            return toolPermissionService.getAllowedTools(tenantId, null);
        }
        return toolPermissionService.getAllowedTools(tenantId, userId);
    }

    // -------------------------------------------------------------------------
    // Provider switching
    // -------------------------------------------------------------------------

    /**
     * Switch (resolve) the LLM provider name for a tenant.
     * Reads from tenant configuration; falls back to default.
     *
     * @param tenantId the tenant
     * @return the provider name
     */
    public String switchProvider(String tenantId) {
        return llmProviderRouter.switchProvider(tenantId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean matches(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private String findLatestUserMessage(List<ChatRequest.Message> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatRequest.Message m = messages.get(i);
            if ("user".equals(m.getRole())) {
                return m.getContent();
            }
        }
        return null;
    }

    private String resolveIndustry(String tenantId) {
        if (tenantId == null) return "default";
        if (tenantId.startsWith("aia-")) return "insurance";
        if (tenantId.startsWith("dialog-") || tenantId.startsWith("hutch-") || tenantId.startsWith("airtel-")) {
            return "telco";
        }
        return "default";
    }
}
