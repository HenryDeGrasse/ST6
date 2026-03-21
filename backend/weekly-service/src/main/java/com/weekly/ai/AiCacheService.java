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
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process cache for AI suggestions.
 *
 * <p>Keyed on {@code orgId + hash(title + description + rcdoTreeVersion)}.
 * TTL: 1 hour (configurable). In production, backed by Redis.
 */
public class AiCacheService {

    private final Duration ttl;
    private final Map<String, CachedEntry<?>> cache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Map<UUID, AtomicLong> orgCacheHits = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> orgCacheMisses = new ConcurrentHashMap<>();

    public AiCacheService(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Gets a cached suggestion result.
     *
     * <p>Increments the hit counter on a valid cache hit, and the miss counter
     * on an expired, absent, or type-mismatched entry. These counters are
     * exposed via {@link #getCacheHits()} / {@link #getCacheMisses()} and used
     * by the admin dashboard AI-usage metrics endpoint.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheKey, Class<T> type) {
        return get(null, cacheKey, type);
    }

    /**
     * Gets a cached suggestion result while attributing the access to a specific org.
     *
     * <p>This powers org-scoped admin dashboard cache metrics. The cache contents are still
     * shared globally, but hit/miss counters can be broken down per org for reporting.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(UUID orgId, String cacheKey, Class<T> type) {
        CachedEntry<?> entry = cache.get(cacheKey);
        if (entry == null || entry.isExpired(Instant.now(), ttl)) {
            cache.remove(cacheKey);
            recordMiss(orgId);
            return Optional.empty();
        }
        if (type.isInstance(entry.value())) {
            recordHit(orgId);
            return Optional.of((T) entry.value());
        }
        recordMiss(orgId);
        return Optional.empty();
    }

    /** Returns the cumulative number of cache hits since service startup. */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /** Returns the cumulative cache hits attributed to the given org since service startup. */
    public long getCacheHits(UUID orgId) {
        if (orgId == null) {
            return 0;
        }
        AtomicLong counter = orgCacheHits.get(orgId);
        return counter != null ? counter.get() : 0;
    }

    /** Returns the cumulative number of cache misses since service startup. */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /** Returns the cumulative cache misses attributed to the given org since service startup. */
    public long getCacheMisses(UUID orgId) {
        if (orgId == null) {
            return 0;
        }
        AtomicLong counter = orgCacheMisses.get(orgId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Puts a value into the cache.
     */
    public <T> void put(String cacheKey, T value) {
        put(null, cacheKey, value);
    }

    /**
     * Puts a value into the cache for a specific org.
     *
     * <p>The stored entry itself is identical to {@link #put(String, Object)}; the org argument
     * exists so callers can use a symmetric API alongside {@link #get(UUID, String, Class)}.
     */
    public <T> void put(UUID orgId, String cacheKey, T value) {
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

    /**
     * Builds a cache key for team RCDO usage aggregations.
     *
     * <p>Keyed on org + week so that all users in the same organisation share
     * the same cached team context for a given week (1-hour TTL).
     */
    public static String buildTeamRcdoUsageKey(UUID orgId, LocalDate weekStart) {
        String raw = orgId + ":team-rcdo:" + weekStart;
        return "ai:team-rcdo-usage:" + sha256(raw);
    }

    /**
     * Builds a cache key for LLM-ranked next-work suggestions.
     *
     * <p>Keyed on org + user + week + context fingerprint so that the cache
     * invalidates when the candidate set or recent history changes.
     * The {@code contextFingerprint} should be a hash of the Phase-1 candidate
     * suggestion IDs, the carry-forward history, and the team coverage gaps —
     * effectively the full input that was sent to the LLM.
     */
    public static String buildNextWorkKey(UUID orgId, UUID userId, LocalDate weekStart,
            String contextFingerprint) {
        String raw = orgId + ":" + userId + ":next-work:" + weekStart + ":" + contextFingerprint;
        return "ai:next-work:" + sha256(raw);
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
     * Clears all cached entries and resets hit/miss counters (for testing).
     */
    public void clear() {
        cache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        orgCacheHits.clear();
        orgCacheMisses.clear();
    }

    /**
     * Returns the number of entries in the cache.
     */
    public int size() {
        return cache.size();
    }

    private void recordHit(UUID orgId) {
        cacheHits.incrementAndGet();
        if (orgId != null) {
            orgCacheHits.computeIfAbsent(orgId, ignored -> new AtomicLong(0)).incrementAndGet();
        }
    }

    private void recordMiss(UUID orgId) {
        cacheMisses.incrementAndGet();
        if (orgId != null) {
            orgCacheMisses.computeIfAbsent(orgId, ignored -> new AtomicLong(0)).incrementAndGet();
        }
    }

    private record CachedEntry<T>(T value, Instant storedAt) {
        boolean isExpired(Instant now, Duration entryTtl) {
            Instant expiresAt = storedAt.plus(entryTtl);
            return !now.isBefore(expiresAt);
        }
    }
}
