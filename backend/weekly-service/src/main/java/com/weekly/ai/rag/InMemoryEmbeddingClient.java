package com.weekly.ai.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EmbeddingClient} for local development and unit tests.
 *
 * <p>Vectors are stored in a {@link ConcurrentHashMap}. Similarity search uses
 * brute-force cosine similarity — adequate for backlogs of &lt;1 000 issues.
 *
 * <p>Embeddings are produced by a deterministic hash-based stub function: the
 * same text always produces the same 1536-dimensional unit vector.  This keeps
 * tests fast, reproducible, and free of external network calls.
 */
public class InMemoryEmbeddingClient implements EmbeddingClient {

    /** Dimensionality must match {@code text-embedding-3-small}. */
    static final int DIMENSIONS = 1536;

    private final ConcurrentHashMap<String, EmbeddingEntry> store = new ConcurrentHashMap<>();

    // ── EmbeddingClient ───────────────────────────────────────────────────────

    @Override
    public float[] embed(String text) {
        return hashEmbed(text, DIMENSIONS);
    }

    @Override
    public void upsert(String id, float[] vector, Map<String, Object> metadata) {
        Map<String, Object> safeMeta = (metadata != null) ? Collections.unmodifiableMap(metadata) : Map.of();
        store.put(id, new EmbeddingEntry(id, vector.clone(), safeMeta));
    }

    @Override
    public List<ScoredMatch> query(float[] vector, int topK, Map<String, Object> filter) {
        List<ScoredMatch> results = new ArrayList<>();
        for (EmbeddingEntry entry : store.values()) {
            if (filter != null && !matchesFilter(entry.metadata(), filter)) {
                continue;
            }
            float score = cosineSimilarity(vector, entry.vector());
            results.add(new ScoredMatch(entry.id(), score, entry.metadata()));
        }
        results.sort((a, b) -> Float.compare(b.score(), a.score()));
        return results.size() > topK ? new ArrayList<>(results.subList(0, topK)) : results;
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the number of vectors currently stored (useful in tests). */
    public int size() {
        return store.size();
    }

    /**
     * Brute-force cosine similarity between two equal-length vectors.
     *
     * @param a first vector
     * @param b second vector
     * @return cosine similarity in [−1, 1], or 0 when either norm is zero
     * @throws IllegalArgumentException if the vectors differ in length
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector dimensions must match: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        if (denom == 0.0) {
            return 0.0f;
        }
        return (float) (dot / denom);
    }

    /**
     * Generates a deterministic unit vector from a text string.
     *
     * <p>Uses the text's {@link String#hashCode()} as the seed for a PRNG so
     * the same input always produces the same output vector.
     *
     * @param text       input text
     * @param dimensions output vector length
     * @return normalised float array of length {@code dimensions}
     */
    public static float[] hashEmbed(String text, int dimensions) {
        float[] vector = new float[dimensions];
        Random rng = new Random(text.hashCode());
        double norm = 0.0;
        for (int i = 0; i < dimensions; i++) {
            vector[i] = rng.nextFloat() * 2.0f - 1.0f;
            norm += (double) vector[i] * vector[i];
        }
        double sqrtNorm = Math.sqrt(norm);
        if (sqrtNorm > 0.0) {
            for (int i = 0; i < dimensions; i++) {
                vector[i] = (float) (vector[i] / sqrtNorm);
            }
        }
        return vector;
    }

    /**
     * Returns {@code true} when every key in {@code filter} is present in
     * {@code metadata} with an equal value.
     */
    private static boolean matchesFilter(Map<String, Object> metadata, Map<String, Object> filter) {
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object metaVal = metadata.get(entry.getKey());
            if (!entry.getValue().equals(metaVal)) {
                return false;
            }
        }
        return true;
    }

    // ── Internal record ───────────────────────────────────────────────────────

    private record EmbeddingEntry(String id, float[] vector, Map<String, Object> metadata) {}
}
