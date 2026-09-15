package com.selfcare.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RAG Service — retrieves relevant context from the knowledge base.
 *
 * In production, this integrates with a vector database (Pinecone, Weaviate, or
 * Elasticsearch with vector search) for semantic similarity search.
 *
 * For now: keyword-based retrieval from cached FAQ/articles.
 *
 * Data sources:
 * - FAQ articles (from content-service)
 * - Product descriptions
 * - Policy documents
 * - Conversation history (short-term context window)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RAG_CACHE_PREFIX = "selfcare:rag:faq:";
    private static final int MAX_CHUNKS = 5;
    private static final int MAX_CHARS = 4000;

    /**
     * Retrieve relevant context for a query.
     *
     * @param query    User's message
     * @param tenantId Tenant
     * @param userId   User
     * @param topK     Number of chunks to retrieve
     * @return Retrieved context formatted for system prompt
     */
    public String retrieve(String query, String tenantId, String userId, int topK) {
        String cacheKey = RAG_CACHE_PREFIX + tenantId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        List<KnowledgeChunk> chunks;
        if (cached instanceof List) {
            chunks = (List<KnowledgeChunk>) cached;
        } else {
            // Load from content service — in production: real vector search
            chunks = loadChunksFromContentService(tenantId);
            redisTemplate.opsForValue().set(cacheKey, chunks);
        }

        // Simple keyword scoring (production: embedding similarity)
        List<ScoredChunk> scored = scoreChunks(query, chunks, topK);
        return formatContext(scored);
    }

    private List<KnowledgeChunk> loadChunksFromContentService(String tenantId) {
        // In production: call content-service or query vector DB with operator
        // isolation (per spec "RAG isolation — each operator has separate
        // document/index/vector namespaces").
        // For now, return mock FAQ knowledge with provenance metadata.
        java.time.Instant now = java.time.Instant.now();
        return Arrays.asList(
                new KnowledgeChunk("faq_balance", "How do I check my balance?",
                        "You can check your balance by: 1) Dialing *123#, 2) Using the mobile app, 3) Logging into the website.",
                        "FAQ", tenantId, "faq_balance_v1", now,
                        "USER,CUSTOMER,ADMIN", 0.9),
                new KnowledgeChunk("faq_recharge", "How do I recharge?",
                        "You can recharge by: 1) Buying a scratch card, 2) Using the mobile app, 3) Bank transfer, 4) Payment via card.",
                        "FAQ", tenantId, "faq_recharge_v1", now,
                        "USER,CUSTOMER,ADMIN", 0.9),
                new KnowledgeChunk("faq_bill", "How do I pay my bill?",
                        "Pay your bill through: 1) Mobile app, 2) Online portal, 3) Bank auto-pay, 4) At a service center.",
                        "ARTICLE", tenantId, "kb_billing_v3", now,
                        "USER,CUSTOMER,ADMIN", 0.85),
                new KnowledgeChunk("faq_plan", "How do I change my plan?",
                        "You can change your plan via the mobile app or by contacting customer service. Plan changes take effect immediately.",
                        "PRODUCT_DOC", tenantId, "plan_change_policy_v2", now,
                        "USER,CUSTOMER,ADMIN", 0.85),
                new KnowledgeChunk("faq_support", "How do I contact support?",
                        "Call 123 from your mobile, or 0112XXXXXX from any phone. Chat is available in the app 24/7.",
                        "FAQ", tenantId, "faq_support_v1", now,
                        "USER,CUSTOMER,ADMIN,SUPPORT_AGENT", 0.95)
        );
    }

    private List<ScoredChunk> scoreChunks(String query, List<KnowledgeChunk> chunks, int topK) {
        String[] queryWords = query.toLowerCase().split("\\s+");
        List<ScoredChunk> scored = new ArrayList<>();

        for (KnowledgeChunk chunk : chunks) {
            int score = 0;
            String chunkText = (chunk.question + " " + chunk.answer).toLowerCase();
            for (String word : queryWords) {
                if (word.length() > 2 && chunkText.contains(word)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    private String formatContext(List<ScoredChunk> scored) {
        if (scored.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Relevant knowledge:\n\n");
        int used = 0;
        for (ScoredChunk sc : scored) {
            if (used > MAX_CHARS) break;
            String text = sc.chunk.answer;
            if (used + text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS - used) + "...";
            }
            sb.append("Q: ").append(sc.chunk.question).append("\n");
            sb.append("A: ").append(text).append("\n\n");
            used += text.length();
        }
        return sb.toString();
    }

    private static class KnowledgeChunk {
        final String id;
        final String question;
        final String answer;
        // Provenance metadata per the AI governance spec
        final String sourceType;     // FAQ, ARTICLE, PRODUCT_DOC, USER_GENERATED, AI_GENERATED
        final String sourceOperator; // dialog-lk, aia-lk, ...
        final String sourceDocId;    // document id in the source system
        final java.time.Instant sourceUpdatedAt;
        final String allowedRoles;   // comma-separated role names allowed to see this chunk
        final double confidence;     // retrieval / source quality score [0,1]

        KnowledgeChunk(String id, String question, String answer) {
            this(id, question, answer, "FAQ", "default", id, java.time.Instant.now(),
                    "USER,CUSTOMER,ADMIN", 0.8);
        }

        KnowledgeChunk(String id, String question, String answer, String sourceType,
                       String sourceOperator, String sourceDocId,
                       java.time.Instant sourceUpdatedAt, String allowedRoles,
                       double confidence) {
            this.id = id;
            this.question = question;
            this.answer = answer;
            this.sourceType = sourceType;
            this.sourceOperator = sourceOperator;
            this.sourceDocId = sourceDocId;
            this.sourceUpdatedAt = sourceUpdatedAt;
            this.allowedRoles = allowedRoles;
            this.confidence = confidence;
        }
    }

    private static class ScoredChunk {
        final KnowledgeChunk chunk;
        final int score;

        ScoredChunk(KnowledgeChunk chunk, int score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
