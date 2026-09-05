package com.omobio.ai.service;

import java.util.List;
import java.util.Map;

/**
 * Interface for LLM providers (Anthropic, OpenAI, Google AI, etc.).
 *
 * Each provider implements this interface to provide a unified chat interface
 * over different LLM backends. The AIModelGateway routes requests to the
 * appropriate provider based on tenant configuration.
 *
 * @see AnthropicProvider
 * @see OpenAiProvider
 */
public interface LlmProvider {

    /**
     * Send a chat request to the LLM provider.
     *
     * @param request the chat request
     * @return the AI response
     */
    AIResponse chat(ChatRequest request);

    /**
     * Send a streaming chat request to the LLM provider.
     *
     * @param request the chat request (must have streaming=true)
     * @return a flux of response chunks
     */
    // Flux<String> chatStream(ChatRequest request);

    /**
     * Provider name identifier (e.g., "anthropic", "openai", "google-ai").
     *
     * @return the provider name
     */
    String getProviderName();
}
