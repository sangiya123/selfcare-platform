package com.selfcare.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Google AI (Gemini) LLM provider — calls the Google AI Generative Language API.
 *
 * Default model: gemini-1.5-pro (configurable via selfcare.ai.providers.google.model).
 * API key is read from environment or tenant configuration.
 *
 * API documentation: https://ai.google.dev/api/generate-content
 */
@Slf4j
@Component("googleLlmProvider")
@RequiredArgsConstructor
public class GoogleAiProvider implements LlmProvider {

    private static final String GOOGLE_AI_BASE = "https://generativelanguage.googleapis.com/v1beta";

    @Value("${selfcare.ai.providers.google.api-key:}")
    private String defaultApiKey;

    @Value("${selfcare.ai.providers.google.model:gemini-1.5-pro}")
    private String defaultModel;

    @Value("${selfcare.ai.providers.google.timeout-seconds:60}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final TenantConfigurationService tenantConfig;

    private static final String INTEGRATION_TYPE = "GOOGLE_AI";

    @Override
    public AIResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();

        String apiKey = resolveApiKey(request.getTenantId());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google AI API key not configured for tenant: " + request.getTenantId());
        }

        String model = request.getExtraParams() != null && request.getExtraParams().get("model") instanceof String s
                ? s
                : resolveDefaultModel(request.getTenantId());
        String baseUrl = resolveBaseUrl(request.getTenantId());
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        Map<String, Object> body = buildRequestBody(request);

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .defaultHeader("content-type", "application/json")
                    .build();

            String responseJson = webClient.post()
                    .uri(url)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            AIResponse response = parseResponse(responseJson, model);
            response.setLatencyMs(System.currentTimeMillis() - start);
            response.setProvider("google-ai");
            response.setModel(model);
            response.setStreamed(request.isStreaming());
            return response;
        } catch (Exception e) {
            log.error("Google AI chat request failed: {}", e.getMessage(), e);
            throw new RuntimeException("Google AI chat request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "google-ai";
    }

    // ------------------------------------------------------------------
    // Request building
    // ------------------------------------------------------------------

    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();

        // Generation config
        Map<String, Object> genConfig = new HashMap<>();
        if (request.getTemperature() != null) {
            genConfig.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            genConfig.put("maxOutputTokens", request.getMaxTokens());
        }
        body.put("generationConfig", genConfig);

        // System instruction
        if (request.getSystemPrompt() != null) {
            Map<String, Object> sysInstr = new HashMap<>();
            sysInstr.put("parts", List.of(Map.of("text", request.getSystemPrompt())));
            sysInstr.put("role", "system");
            body.put("systemInstruction", sysInstr);
        }

        // Contents (messages)
        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatRequest.Message m : request.getMessages()) {
                if ("system".equals(m.getRole())) {
                    continue;
                }
                Map<String, Object> content = new HashMap<>();
                Map<String, String> part = new HashMap<>();
                part.put("text", m.getContent() != null ? m.getContent() : "");
                content.put("role", "user".equals(m.getRole()) ? "user" : "model");
                content.put("parts", List.of(part));
                contents.add(content);
            }
        }
        body.put("contents", contents);

        // Tools (function declarations)
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> functionDeclarations = new ArrayList<>();
            for (ChatRequest.ToolDefinition t : request.getTools()) {
                Map<String, Object> decl = new HashMap<>();
                decl.put("name", t.getName());
                decl.put("description", t.getDescription());
                decl.put("parameters", t.getInputSchema() != null ? t.getInputSchema() : Map.of("type", "object"));
                functionDeclarations.add(decl);
            }
            Map<String, Object> tools = new HashMap<>();
            tools.put("functionDeclarations", functionDeclarations);
            body.put("tools", List.of(tools));
        }

        return body;
    }

    // ------------------------------------------------------------------
    // Response parsing
    // ------------------------------------------------------------------

    private AIResponse parseResponse(String responseJson, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidate = root.path("candidates").path(0);
            JsonNode content = candidate.path("content");
            JsonNode parts = content.path("parts");

            StringBuilder text = new StringBuilder();
            List<AIResponse.ToolCallResult> toolsUsed = new ArrayList<>();

            for (JsonNode part : parts) {
                if (part.has("text")) {
                    text.append(part.path("text").asText());
                } else if (part.has("functionCall")) {
                    JsonNode fc = part.path("functionCall");
                    AIResponse.ToolCallResult tcr = AIResponse.ToolCallResult.builder()
                            .toolName(fc.path("name").asText())
                            .arguments(fc.path("args").toString())
                            .success(true)
                            .build();
                    toolsUsed.add(tcr);
                }
            }

            AIResponse.TokenUsage usage = AIResponse.TokenUsage.builder()
                    .promptTokens(root.path("usageMetadata").path("promptTokenCount").asInt(0))
                    .completionTokens(root.path("usageMetadata").path("candidatesTokenCount").asInt(0))
                    .totalTokens(root.path("usageMetadata").path("totalTokenCount").asInt(0))
                    .build();

            return AIResponse.builder()
                    .content(text.toString())
                    .toolsUsed(toolsUsed)
                    .tokenUsage(usage)
                    .model(model)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Google AI response: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .content(responseJson)
                    .build();
        }
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
     * Resolve the Google AI API base for a tenant from the DB-backed integration
     * base URL, falling back to the platform default.
     */
    private String resolveBaseUrl(String tenantId) {
        String base = tenantConfig.getBaseUrl(tenantId, INTEGRATION_TYPE);
        if (base != null && !base.isBlank() && !base.startsWith("${")) {
            return base;
        }
        return GOOGLE_AI_BASE;
    }

    private String resolveDefaultModel(String tenantId) {
        String dbModel = tenantConfig.getCredential(tenantId, INTEGRATION_TYPE, "defaultModel");
        if (dbModel != null && !dbModel.isBlank() && !dbModel.startsWith("${")) {
            return dbModel;
        }
        return defaultModel;
    }
}
