package com.omobio.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Anthropic LLM provider — calls the Anthropic Messages API.
 *
 * Default model: claude-sonnet-4-5 (configurable via omobio.ai.providers.anthropic.model).
 * API key is read from environment or tenant configuration.
 *
 * API documentation: https://docs.anthropic.com/claude/reference/messages_post
 */
@Slf4j
@Component("anthropicLlmProvider")
@RequiredArgsConstructor
public class AnthropicProvider implements LlmProvider {

    private static final String ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Value("${omobio.ai.providers.anthropic.api-key:}")
    private String defaultApiKey;

    @Value("${omobio.ai.providers.anthropic.model:claude-sonnet-4-5}")
    private String defaultModel;

    @Value("${omobio.ai.providers.anthropic.max-tokens:4096}")
    private int defaultMaxTokens;

    @Value("${omobio.ai.providers.anthropic.timeout-seconds:60}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public AIResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();

        String apiKey = resolveApiKey(request.getTenantId());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API key not configured for tenant: " + request.getTenantId());
        }

        Map<String, Object> body = buildRequestBody(request);
        String model = request.getExtraParams() != null && request.getExtraParams().get("model") instanceof String s
                ? s
                : defaultModel;

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(ANTHROPIC_MESSAGES_URL)
                    .defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                    .defaultHeader("content-type", "application/json")
                    .build();

            String responseJson = webClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            AIResponse response = parseResponse(responseJson, model);
            response.setLatencyMs(System.currentTimeMillis() - start);
            response.setProvider("anthropic");
            response.setModel(model);
            response.setStreamed(request.isStreaming());
            return response;
        } catch (Exception e) {
            log.error("Anthropic chat request failed: {}", e.getMessage(), e);
            throw new RuntimeException("Anthropic chat request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    // ------------------------------------------------------------------
    // Request building
    // ------------------------------------------------------------------

    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", defaultModel);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : defaultMaxTokens);

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getSystemPrompt() != null) {
            body.put("system", request.getSystemPrompt());
        }

        // Convert messages to Anthropic format
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatRequest.Message m : request.getMessages()) {
                if ("system".equals(m.getRole())) {
                    continue; // handled via system field
                }
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", m.getRole());
                msg.put("content", m.getContent());
                messages.add(msg);
            }
        }
        body.put("messages", messages);

        // Convert tools to Anthropic format
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ChatRequest.ToolDefinition t : request.getTools()) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("name", t.getName());
                tool.put("description", t.getDescription());
                tool.put("input_schema", t.getInputSchema() != null ? t.getInputSchema() : Map.of("type", "object"));
                tools.add(tool);
            }
            body.put("tools", tools);
        }

        return body;
    }

    // ------------------------------------------------------------------
    // Response parsing
    // ------------------------------------------------------------------

    private AIResponse parseResponse(String responseJson, String model) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            StringBuilder content = new StringBuilder();
            List<AIResponse.ToolCallResult> toolsUsed = new ArrayList<>();

            for (JsonNode block : root.path("content")) {
                String type = block.path("type").asText();
                if ("text".equals(type)) {
                    content.append(block.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    AIResponse.ToolCallResult tcr = AIResponse.ToolCallResult.builder()
                            .toolName(block.path("name").asText())
                            .arguments(block.path("input").toString())
                            .success(true)
                            .build();
                    toolsUsed.add(tcr);
                }
            }

            AIResponse.TokenUsage usage = AIResponse.TokenUsage.builder()
                    .promptTokens(root.path("usage").path("input_tokens").asInt(0))
                    .completionTokens(root.path("usage").path("output_tokens").asInt(0))
                    .totalTokens(root.path("usage").path("input_tokens").asInt(0)
                            + root.path("usage").path("output_tokens").asInt(0))
                    .build();

            return AIResponse.builder()
                    .content(content.toString())
                    .toolsUsed(toolsUsed)
                    .tokenUsage(usage)
                    .model(model)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response: {}", e.getMessage(), e);
            return AIResponse.builder()
                    .content(responseJson)
                    .build();
        }
    }

    /**
     * Resolve the API key for a tenant.
     * In production: fetch from tenant configuration service.
     * For now: use the default key from properties.
     */
    private String resolveApiKey(String tenantId) {
        return defaultApiKey;
    }
}
