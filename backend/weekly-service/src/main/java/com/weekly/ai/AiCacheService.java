package com.weekly.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process cache for AI suggestions.
 *
 * <p>Keyed on {@code orgId + hash(title + description + rcdoTreeVersion)}.
 * TTL: 1 hour (configurable). In production, backed by Redis.
 */
public class AiCacheService {

    private final Duration ttl;
    private final Map<String, CachedEntry<?>> cache = new ConcurrentHashMap<>();

    public AiCacheService(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Gets a cached suggestion result.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheKey, Class<T> type) {
        CachedEntry<?> entry = cache.get(cacheKey);
        if (entry == null || entry.isExpired(Instant.now(), ttl)) {
            cache.remove(cacheKey);
            return Optional.empty();
        }
        if (type.isInstance(entry.value())) {
            return Optional.of((T) entry.value());
        }
        return Optional.empty();
    }

    /**
     * Puts a value into the cache.
     */
    public <T> void put(String cacheKey, T value) {
        cache.put(cacheKey, new CachedEntry<>(value, Instant.now()));
    }

    /**
     * Builds a cache key for RCDO suggestions.
     */
    public static String buildSuggestKey(UUID orgId, String title, String description, String rcdoTreeVersion) {
        String raw = orgId + ":" + title + ":" + (description != null ? description : "") + ":" + rcdoTreeVersion;
        return "ai:suggest:" + sha256(raw);
    }

    /**
     * Builds a cache key for reconciliation drafts.
     */
    public static String buildDraftKey(UUID orgId, UUID planId, String commitHash) {
        String raw = orgId + ":" + planId + ":" + commitHash;
        return "ai:draft:" + sha256(raw);
    }

    /**
     * Builds a cache key for manager insight summaries.
     */
    public static String buildManagerInsightsKey(UUID orgId, UUID managerId, LocalDate weekStart, String contextHash) {
        String raw = orgId + ":" + managerId + ":" + weekStart + ":" + contextHash;
        return "ai:manager-insights:" + sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in the JDK
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Clears all cached entries (for testing).
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the number of entries in the cache.
     */
    public int size() {
        return cache.size();
    }

    private record CachedEntry<T>(T value, Instant storedAt) {
        boolean isExpired(Instant now, Duration entryTtl) {
            return now.isAfter(storedAt.plus(entryTtl));
        }
    }
}
