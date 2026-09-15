package com.selfcare.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.*;

/**
 * OpenAI LLM Provider — calls the OpenAI Chat Completions API.
 *
 * Supports:
 * - Standard chat completions
 * - Streaming responses (SSE via chatStream)
 * - Tool calling (function calling)
 * - Vision (when image URLs are in the message content)
 *
 * Model: gpt-4o-mini by default (configurable via selfcare.ai.providers.openai.model).
 */
@Slf4j
@Component("openaiLlmProvider")
@RequiredArgsConstructor
public class OpenAiProvider implements LlmProvider {

    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${selfcare.ai.providers.openai.api-key:}")
    private String defaultApiKey;

    @Value("${selfcare.ai.providers.openai.model:gpt-4o-mini}")
    private String defaultModel;

    @Value("${selfcare.ai.providers.openai.max-tokens:4096}")
    private int defaultMaxTokens;

    @Value("${selfcare.ai.providers.openai.timeout-seconds:60}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final TenantConfigurationService tenantConfig;

    private static final String INTEGRATION_TYPE = "OPENAI";

    @Override
    public AIResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String apiKey = resolveApiKey(request.getTenantId());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key not configured for tenant: " + request.getTenantId());
        }

        String model = request.getExtraParams() != null &&
                request.getExtraParams().get("model") instanceof String s ? s : resolveDefaultModel(request.getTenantId());

        Map<String, Object> body = buildRequestBody(request, model);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(resolveChatUrl(request.getTenantId()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .block();

            return parseResponse(response, request.getSessionId(), model, start);
        } catch (Exception e) {
            log.error("OpenAI chat failed: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    // -------------------------------------------------------------------------
    // Request building
    // -------------------------------------------------------------------------

    private Map<String, Object> buildRequestBody(ChatRequest request, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);

        // Temperature
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        } else {
            body.put("temperature", 0.7);
        }

        // Max tokens
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        } else {
            body.put("max_tokens", defaultMaxTokens);
        }

        // Messages
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                if (msg.getToolCall() != null) {
                    // Assistant message with tool call result
                    messages.add(Map.of(
                            "role", "assistant",
                            "content", msg.getContent() != null ? msg.getContent() : "",
                            "tool_calls", List.of(Map.of(
                                    "id", msg.getToolCall().getName() + "-" + System.currentTimeMillis(),
                                    "type", "function",
                                    "function", Map.of(
                                            "name", msg.getToolCall().getName(),
                                            "arguments", msg.getToolCall().getArguments() != null
                                                    ? msg.getToolCall().getArguments() : "{}"
                                    )
                            ))
                    ));
                } else {
                    messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }
            }
        }
        body.put("messages", messages);

        // Tools (OpenAI function calling format)
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ChatRequest.ToolDefinition tool : request.getTools()) {
                tools.add(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", tool.getName(),
                                "description", tool.getDescription() != null ? tool.getDescription() : "",
                                "parameters", tool.getInputSchema() != null ? tool.getInputSchema()
                                        : Map.of("type", "object", "properties", Map.of())
                        )
                ));
            }
            body.put("tools", tools);
        }

        return body;
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private AIResponse parseResponse(Map<String, Object> response, String sessionId,
                                    String model, long start) {
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return AIResponse.builder()
                    .content("")
                    .sessionId(sessionId)
                    .provider("openai")
                    .model(model)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");

        String content = message != null ? (String) message.get("content") : "";

        // Parse tool calls (OpenAI function calling)
        List<AIResponse.ToolCallResult> toolCalls = new ArrayList<>();
        List<Map<String, Object>> openaiTools = (List<Map<String, Object>>) message.get("tool_calls");
        if (openaiTools != null) {
            for (Map<String, Object> tc : openaiTools) {
                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                toolCalls.add(AIResponse.ToolCallResult.builder()
                        .toolName((String) fn.get("name"))
                        .arguments((String) fn.get("arguments"))
                        .success(true)
                        .build());
            }
        }

        return AIResponse.builder()
                .content(content)
                .sessionId(sessionId)
                .toolsUsed(toolCalls.isEmpty() ? null : toolCalls)
                .provider("openai")
                .model(model)
                .tokenUsage(AIResponse.TokenUsage.builder()
                        .promptTokens(usage != null ? ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue() : 0)
                        .completionTokens(usage != null ? ((Number) usage.getOrDefault("completion_tokens", 0)).intValue() : 0)
                        .totalTokens(usage != null ? ((Number) usage.getOrDefault("total_tokens", 0)).intValue() : 0)
                        .build())
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private String resolveApiKey(String tenantId) {
        String dbKey = tenantConfig.getCredential(tenantId, INTEGRATION_TYPE, "apiKey");
        String key = resolveCredential(dbKey);
        if (key != null) {
            return key;
        }
        if (defaultApiKey != null && !defaultApiKey.isBlank() && !defaultApiKey.startsWith("secret:")) {
            return defaultApiKey;
        }
        return null;
    }

    private String resolveCredential(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.startsWith("secret:")) {
            return null;
        }
        if (value.startsWith("env:")) {
            String envKey = value.substring(4);
            String resolved = System.getenv(envKey);
            if (resolved != null && !resolved.isBlank() && !resolved.startsWith("secret:")) {
                return resolved;
            }
            return null;
        }
        return value;
    }

    /**
     * Resolve the OpenAI-compatible chat URL for a tenant from the DB-backed
     * integration base URL, falling back to the platform default. This allows a
     * free-tier or self-hosted OpenAI-compatible endpoint (e.g. Ollama, OpenRouter,
     * Groq) to be configured per tenant without any code change.
     */
    private String resolveChatUrl(String tenantId) {
        String base = tenantConfig.getBaseUrl(tenantId, INTEGRATION_TYPE);
        if (base != null && !base.isBlank() && !base.startsWith("${")) {
            return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
        }
        return OPENAI_CHAT_URL;
    }

    private String resolveDefaultModel(String tenantId) {
        String dbModel = tenantConfig.getCredential(tenantId, INTEGRATION_TYPE, "defaultModel");
        if (dbModel != null && !dbModel.isBlank() && !dbModel.startsWith("${")) {
            return dbModel;
        }
        return defaultModel;
    }
}
