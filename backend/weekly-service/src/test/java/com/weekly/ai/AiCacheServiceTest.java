package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiCacheService}.
 */
class AiCacheServiceTest {

    @Test
    void storesAndRetrievesValues() {
        AiCacheService cache = new AiCacheService(Duration.ofHours(1));
        String key = "test-key";
        cache.put(key, "hello");

        Optional<String> result = cache.get(key, String.class);
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void returnsMissForExpiredEntries() {
        // Use a zero-duration TTL to expire immediately
        AiCacheService cache = new AiCacheService(Duration.ZERO);
        String key = "test-key";
        cache.put(key, "hello");

        Optional<String> result = cache.get(key, String.class);
        assertFalse(result.isPresent(), "Expired entries should not be returned");
    }

    @Test
    void returnsMissForUnknownKeys() {
        AiCacheService cache = new AiCacheService(Duration.ofHours(1));
        Optional<String> result = cache.get("nonexistent", String.class);
        assertFalse(result.isPresent());
    }

    @Test
    void buildSuggestKeyIsDeterministic() {
        UUID orgId = UUID.randomUUID();
        String key1 = AiCacheService.buildSuggestKey(orgId, "title", "desc", "v1");
        String key2 = AiCacheService.buildSuggestKey(orgId, "title", "desc", "v1");
        assertEquals(key1, key2);
    }

    @Test
    void buildSuggestKeyVariesByInput() {
        UUID orgId = UUID.randomUUID();
        String key1 = AiCacheService.buildSuggestKey(orgId, "title1", "desc", "v1");
        String key2 = AiCacheService.buildSuggestKey(orgId, "title2", "desc", "v1");
        assertFalse(key1.equals(key2), "Different titles should produce different keys");
    }

    @Test
    void buildManagerInsightsKeyIncludesManagerAndWeek() {
        UUID orgId = UUID.randomUUID();
        UUID managerA = UUID.randomUUID();
        UUID managerB = UUID.randomUUID();
        LocalDate weekStart = LocalDate.of(2026, 3, 9);

        String keyA = AiCacheService.buildManagerInsightsKey(orgId, managerA, weekStart, "ctx");
        String keyB = AiCacheService.buildManagerInsightsKey(orgId, managerB, weekStart, "ctx");

        assertFalse(keyA.equals(keyB), "Different managers must not share AI insight cache entries");
    }

    @Test
    void buildNextWorkKeyIncludesUserWeekAndContextFingerprint() {
        UUID orgId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        LocalDate weekA = LocalDate.of(2026, 3, 9);
        LocalDate weekB = LocalDate.of(2026, 3, 16);

        String keyA = AiCacheService.buildNextWorkKey(orgId, userA, weekA, "ctx-a");
        String keyB = AiCacheService.buildNextWorkKey(orgId, userB, weekA, "ctx-a");
        String keyC = AiCacheService.buildNextWorkKey(orgId, userA, weekB, "ctx-a");
        String keyD = AiCacheService.buildNextWorkKey(orgId, userA, weekA, "ctx-b");

        assertFalse(keyA.equals(keyB), "Different users must not share cached next-work rankings");
        assertFalse(keyA.equals(keyC), "Different planning weeks must not share cached next-work rankings");
        assertFalse(keyA.equals(keyD), "Different prompt context fingerprints must invalidate the cache");
    }

    @Test
    void tracksOrgScopedHitAndMissCounters() {
        AiCacheService cache = new AiCacheService(Duration.ofHours(1));
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();

        cache.put(orgA, "key-a", "value-a");
        cache.get(orgA, "key-a", String.class);
        cache.get(orgA, "missing-a", String.class);
        cache.put(orgB, "key-b", "value-b");
        cache.get(orgB, "key-b", String.class);

        assertEquals(2, cache.getCacheHits());
        assertEquals(1, cache.getCacheMisses());
        assertEquals(1, cache.getCacheHits(orgA));
        assertEquals(1, cache.getCacheMisses(orgA));
        assertEquals(1, cache.getCacheHits(orgB));
        assertEquals(0, cache.getCacheMisses(orgB));
    }

    @Test
    void clearRemovesAllEntries() {
        AiCacheService cache = new AiCacheService(Duration.ofHours(1));
        UUID orgId = UUID.randomUUID();
        cache.put(orgId, "key1", "val1");
        cache.put(orgId, "key2", "val2");
        cache.get(orgId, "key1", String.class);
        cache.get(orgId, "missing", String.class);
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.getCacheHits());
        assertEquals(0, cache.getCacheMisses());
        assertEquals(0, cache.getCacheHits(orgId));
        assertEquals(0, cache.getCacheMisses(orgId));
    }
}
