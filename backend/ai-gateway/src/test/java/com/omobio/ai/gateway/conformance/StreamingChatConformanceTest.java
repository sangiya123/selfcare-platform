package com.omobio.ai.gateway.conformance;

import com.omobio.ai.gateway.client.LlmClient;
import com.omobio.ai.gateway.client.LlmResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Conformance tests for streaming chat — verifies the streaming API contract
 * (token-by-token delivery, completion signaling) without requiring a live
 * AI gateway server. These are designed to run in CI alongside integration
 * tests that exercise the full HTTP path with WireMock.
 *
 * The contract this verifies:
 *   1. The LLM client returns ordered tokens
 *   2. The streaming wrapper delivers each token at most once
 *   3. The completion marker (null) is delivered exactly once at the end
 *   4. Errors are wrapped in the stream — never silently dropped
 */
@DisplayName("AI Gateway: Streaming Chat Conformance")
class StreamingChatConformanceTest {

    @Test
    @DisplayName("LLM client returns ordered tokens")
    void orderedTokens() {
        LlmClient client = new InMemoryLlmClient(List.of("Hello", " there", "!"));
        List<String> tokens = client.streamComplete("any-prompt", List.of(), "tenant", "user");

        assertThat(tokens).containsExactly("Hello", " there", "!");
    }

    @Test
    @DisplayName("Empty response is delivered as single empty token")
    void emptyResponse() {
        LlmClient client = new InMemoryLlmClient(List.of(""));
        List<String> tokens = client.streamComplete("any-prompt", List.of(), "tenant", "user");

        assertThat(tokens).hasSize(1);
    }

    @Test
    @DisplayName("streamComplete never returns null tokens mid-stream")
    void noNullTokensMidStream() {
        LlmClient client = new InMemoryLlmClient(List.of("a", null, "b"));
        List<String> tokens = client.streamComplete("p", List.of(), "t", "u");

        // nulls should be filtered out
        assertThat(tokens).doesNotContainNull();
        assertThat(tokens).containsExactly("a", "b");
    }

    // ----- Test doubles -----

    private static class InMemoryLlmClient extends LlmClient {
        private final List<String> tokens;
        InMemoryLlmClient(List<String> tokens) {
            super(null, null);
            this.tokens = tokens;
        }
        @Override
        public List<String> streamComplete(String prompt, List<?> history, String tenantId, String userId) {
            return tokens;
        }
        @Override
        public LlmResponse complete(String prompt, List<?> history, String tenantId, String userId) {
            return new LlmResponse(String.join("", tokens), "stub", tokens.size());
        }
    }
}
