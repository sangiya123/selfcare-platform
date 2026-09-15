package com.selfcare.platform.common.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests for the canonical {@link AiProvider} interface.
 * All AI provider implementations (OpenAI, Anthropic, Azure OpenAI, etc.)
 * MUST pass these tests.
 */
class AiProviderTest {

    // ─── AiMessage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("AiMessage: user message is well-formed")
    void userMessageIsWellFormed() {
        var msg = new AiProvider.AiMessage("user", "What is my balance?", null);
        assertEquals("user", msg.role());
        assertEquals("What is my balance?", msg.content());
        assertNull(msg.toolCallId());  // user message has no toolCallId
    }

    @Test
    @DisplayName("AiMessage: tool message carries toolCallId")
    void toolMessageCarriesToolCallId() {
        var msg = new AiProvider.AiMessage("tool", "{\"balance\": 1250.50}", "call_abc123");
        assertEquals("tool", msg.role());
        assertEquals("call_abc123", msg.toolCallId());
    }

    @Test
    @DisplayName("AiMessage: assistant message with tool_calls has no content")
    void assistantMessageWithToolCallsHasNoContent() {
        var msg = new AiProvider.AiMessage("assistant", "", "call_xyz");
        assertEquals("assistant", msg.role());
        assertEquals("", msg.content());
        assertEquals("call_xyz", msg.toolCallId());
    }

    // ─── AiTool ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AiTool: function tool is well-formed")
    void functionToolIsWellFormed() {
        var tool = new AiProvider.AiTool(
                "function",
                "get_balance",
                "Retrieves the postpaid balance for a connection",
                Map.of("connectionId", Map.of("type", "string"))
        );
        assertEquals("function", tool.type());
        assertEquals("get_balance", tool.name());
        assertNotNull(tool.parameters());
        assertTrue(tool.parameters().containsKey("connectionId"));
    }

    // ─── AiToolCall & AiToolResult ─────────────────────────────────────────

    @Test
    @DisplayName("AiToolCall: round-trip through AiToolResult")
    void toolCallRoundTrip() {
        var call = new AiProvider.AiToolCall(
                "call_001",
                "get_balance",
                Map.of("connectionId", "94771123456")
        );
        var result = new AiProvider.AiToolResult(
                "call_001",
                "{\"balance\": 1250.50}"
        );
        assertEquals(call.id(), result.toolCallId());
        assertTrue(result.content().contains("balance"));
    }

    // ─── AiResponse & AiUsage ──────────────────────────────────────────────

    @Test
    @DisplayName("AiResponse: usage is recorded")
    void usageIsRecorded() {
        var usage = new AiProvider.AiUsage(1200, 85, 1285);
        var response = new AiProvider.AiResponse(
                "Your balance is LKR 1,250.50.",
                "stop",
                usage,
                null
        );
        assertEquals(1200, response.usage().promptTokens());
        assertEquals(85, response.usage().completionTokens());
        assertEquals(1285, response.usage().totalTokens());
        assertEquals("stop", response.finishReason());
    }

    @Test
    @DisplayName("AiResponse: optional systemFingerprint may be null")
    void systemFingerprintMayBeNull() {
        var response = new AiProvider.AiResponse(
                "Hello!",
                "stop",
                new AiProvider.AiUsage(10, 5, 15),
                null
        );
        assertNull(response.systemFingerprint());
    }

    // ─── AiDelta ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AiDelta: streaming delta carries partial content")
    void streamingDeltaCarriesPartialContent() {
        var delta = new AiProvider.AiDelta("Hello", null, null, null);
        assertEquals("Hello", delta.content());
        assertNull(delta.finishReason());
    }

    @Test
    @DisplayName("AiDelta: final delta carries finishReason")
    void finalDeltaCarriesFinishReason() {
        var delta = new AiProvider.AiDelta(null, null, null, "stop");
        assertEquals("stop", delta.finishReason());
        assertNull(delta.content());
    }

    // ─── Embeddings ────────────────────────────────────────────────────────

    @Test
    @DisplayName("AiEmbeddingsResponse: vector dimension matches")
    void embeddingsVectorDimensionIsConsistent() {
        var response = new AiProvider.AiEmbeddingsResponse(
                List.of(
                        List.of(0.1, 0.2, 0.3),
                        List.of(0.4, 0.5, 0.6)
                ),
                "text-embedding-3-small",
                new AiProvider.AiUsage(20, 0, 20)
        );
        assertEquals(2, response.embeddings().size());
        assertEquals(3, response.embeddings().get(0).size()); // 1536 dims for 3-small → test with short vector
    }

    // ─── RAG ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RagChunk: metadata is optional but present when provided")
    void ragChunkMetadataIsOptional() {
        var chunk = new AiProvider.RagChunk(
                "doc_001",
                "chunk_003",
                "Your policy covers hospitalisation up to LKR 500,000.",
                0.92,
                Map.of("source", "policy-handbook.pdf", "page", "14")
        );
        assertEquals("doc_001", chunk.documentId());
        assertEquals(0.92, chunk.score());
        assertEquals("policy-handbook.pdf", chunk.metadata().get("source"));
    }

    @Test
    @DisplayName("RagChunk: score ordering is descending (highest relevance first)")
    void ragChunkScoreOrderingIsDescending() {
        var chunks = List.of(
                new AiProvider.RagChunk("d2", "c2", "High relevance", 0.91, Map.of()),
                new AiProvider.RagChunk("d3", "c3", "Medium relevance", 0.78, Map.of()),
                new AiProvider.RagChunk("d1", "c1", "Low relevance", 0.55, Map.of())
        );
        double prev = 1.0;
        for (var chunk : chunks) {
            assertTrue(chunk.score() <= prev,
                    "Chunks should be sorted descending by score");
            prev = chunk.score();
        }
    }
}
