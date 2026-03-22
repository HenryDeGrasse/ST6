package com.weekly.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over vector-store backends (Pinecone or in-memory fallback).
 *
 * <p>Implementations must be thread-safe. The default bean is resolved by
 * {@link RagConfiguration} based on the {@code weekly.rag.provider} property.
 */
public interface EmbeddingClient {

    /**
     * Converts a text string into a dense embedding vector.
     *
     * @param text the text to embed (max ~8192 tokens for text-embedding-3-small)
     * @return normalised float array of length 1536
     */
    float[] embed(String text);

    /**
     * Upserts a vector into the vector store.
     *
     * @param id       unique identifier for this vector (e.g., issue UUID)
     * @param vector   dense float array of length 1536
     * @param metadata arbitrary key-value pairs stored alongside the vector
     */
    void upsert(String id, float[] vector, Map<String, Object> metadata);

    /**
     * Queries the vector store for the top-K most similar vectors.
     *
     * @param vector the query vector of length 1536
     * @param topK   maximum number of results to return
     * @param filter optional metadata filter ({@code null} means no filter);
     *               in-memory implementation does an exact-match on all provided keys
     * @return list of at most {@code topK} matches, ordered by descending similarity
     */
    List<ScoredMatch> query(float[] vector, int topK, Map<String, Object> filter);

    /**
     * Deletes a vector by its ID. No-op if the ID does not exist.
     *
     * @param id the vector ID to delete
     */
    void delete(String id);
}
