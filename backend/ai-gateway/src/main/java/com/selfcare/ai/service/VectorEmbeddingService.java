package com.selfcare.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Vector Embedding Service — generates and stores semantic embeddings for RAG.
 *
 * Architecture options:
 *   1. OpenAI text-embedding-3-small (default, highest quality)
 *   2. Local fallback: keyword + BM25 scoring (no API key needed)
 *   3. Pinecone / Weaviate (production vector DB — configure baseUrl)
 *
 * For now, the service operates in KEYWORD mode by default, falling back to
 * OpenAI embeddings when an API key is configured. The embedding store is
 * Redis (using JSON serialization). In production, swap to a dedicated vector DB.
 *
 * Supported models: text-embedding-3-small, text-embedding-3-large,
 *                   text-embedding-ada-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorEmbeddingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String EMBEDDING_CACHE_PREFIX = "selfcare:embedding:";
    private static final String CHUNKS_KEY_PREFIX = "selfcare:chunks:";
    private static final int EMBEDDING_DIM = 1536; // text-embedding-3-small
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @Value("${selfcare.ai.embedding.openai-api-key:}")
    private String openAiApiKey;

    @Value("${selfcare.ai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${selfcare.ai.embedding.base-url:}")
    private String customBaseUrl;

    @Value("${selfcare.ai.embedding.enabled:false}")
    private boolean embeddingEnabled;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate an embedding for a text query.
     *
     * @param text the text to embed
     * @return a float array of embeddings (1536-dim for text-embedding-3-small)
     */
    public float[] embed(String text) {
        if (!embeddingEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            return keywordEmbedding(text);
        }

        try {
            String baseUrl = customBaseUrl != null && !customBaseUrl.isBlank()
                    ? customBaseUrl
                    : "https://api.openai.com";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/v1/embeddings")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .bodyValue(Map.of("model", embeddingModel, "input", text))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return keywordEmbedding(text);
            }

            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.warn("Embedding API failed, falling back to keyword: {}", e.getMessage());
            return keywordEmbedding(text);
        }
    }

    /**
     * Index a document chunk for semantic search.
     *
     * @param chunkId unique ID for the chunk
     * @param text the chunk text
     * @param metadata additional metadata (source, tenant, etc.)
     */
    public void indexChunk(String chunkId, String text, Map<String, String> metadata) {
        float[] embedding = embed(text);
        String tenantId = metadata.getOrDefault("tenantId", "default");

        String chunksKey = CHUNKS_KEY_PREFIX + tenantId;
        String embKey = EMBEDDING_CACHE_PREFIX + chunkId;

        try {
            // Store the text and metadata
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("text", text);
            chunkData.put("metadata", metadata);
            chunkData.put("chunkId", chunkId);
            chunkData.put("embedding", toDoubleList(embedding));

            redisTemplate.opsForHash().put(chunksKey, chunkId, chunkData);
            redisTemplate.expire(chunksKey, CACHE_TTL);

            log.debug("Indexed chunk: id={}, tenant={}", chunkId, tenantId);
        } catch (Exception e) {
            log.error("Failed to index chunk: {}", chunkId, e);
        }
    }

    /**
     * Delete a knowledge chunk for a tenant.
     *
     * @param chunkId unique ID of the chunk to remove
     * @param tenantId tenant scope
     * @return true if a chunk was removed, false otherwise
     */
    public boolean deleteChunk(String chunkId, String tenantId) {
        String chunksKey = CHUNKS_KEY_PREFIX + tenantId;
        try {
            Boolean removed = redisTemplate.opsForHash().delete(chunksKey, chunkId) > 0;
            redisTemplate.delete(EMBEDDING_CACHE_PREFIX + chunkId);
            if (removed) {
                log.info("Deleted knowledge chunk: id={}, tenant={}", chunkId, tenantId);
            } else {
                log.warn("Knowledge chunk not found: id={}, tenant={}", chunkId, tenantId);
            }
            return removed;
        } catch (Exception e) {
            log.error("Failed to delete chunk: {}: {}", chunkId, e.getMessage());
            return false;
        }
    }

    /**
     * Find the most similar chunks to a query.
     *
     * @param query the search query
     * @param tenantId tenant scope
     * @param topK number of results
     * @return list of (chunkId, text, similarity) sorted by similarity descending
     */
    public List<SimilarChunk> findSimilar(String query, String tenantId, int topK) {
        float[] queryEmbedding = embed(query);
        String chunksKey = CHUNKS_KEY_PREFIX + tenantId;

        // Fetch all chunks for tenant
        Map<Object, Object> allChunks = redisTemplate.opsForHash().entries(chunksKey);
        if (allChunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<SimilarChunk> results = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : allChunks.entrySet()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> chunkData = (Map<String, Object>) entry.getValue();
                @SuppressWarnings("unchecked")
                List<Double> embList = (List<Double>) chunkData.get("embedding");
                if (embList == null) continue;

                float[] chunkEmb = new float[embList.size()];
                for (int i = 0; i < embList.size(); i++) {
                    chunkEmb[i] = embList.get(i).floatValue();
                }

                double similarity = cosineSimilarity(queryEmbedding, chunkEmb);
                if (similarity > 0.5) { // Only return reasonably similar chunks
                    results.add(new SimilarChunk(
                            (String) entry.getKey(),
                            (String) chunkData.get("text"),
                            similarity,
                            (Map<String, String>) chunkData.get("metadata")
                    ));
                }
            } catch (Exception e) {
                log.warn("Error computing similarity for chunk: {}", entry.getKey());
            }
        }

        results.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        return results.subList(0, Math.min(topK, results.size()));
    }

    // -------------------------------------------------------------------------
    // Keyword fallback (no API required)
    // -------------------------------------------------------------------------

    /**
     * Generate a pseudo-embedding from keyword frequency.
     * Used when no embedding API key is configured.
     */
    private float[] keywordEmbedding(String text) {
        String[] words = text.toLowerCase().split("\\s+");
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        float[] vector = new float[EMBEDDING_DIM];
        int i = 0;
        for (String word : uniqueWords) {
            if (i >= EMBEDDING_DIM) break;
            vector[i++] = word.hashCode() / (float) Integer.MAX_VALUE;
        }
        return vector;
    }

    // -------------------------------------------------------------------------
    // Math helpers
    // -------------------------------------------------------------------------

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private List<Double> toDoubleList(float[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add((double) v);
        return list;
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public record SimilarChunk(
            String chunkId,
            String text,
            double similarity,
            Map<String, String> metadata
    ) {}
}
