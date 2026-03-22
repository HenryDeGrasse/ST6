package com.weekly.ai.rag;

import java.util.Map;

/**
 * A single result from a vector similarity search.
 *
 * @param id       the vector ID (usually the issue UUID as a string)
 * @param score    cosine similarity score in [0, 1]; higher is more similar
 * @param metadata key-value metadata associated with the stored vector
 */
public record ScoredMatch(String id, float score, Map<String, Object> metadata) {}
