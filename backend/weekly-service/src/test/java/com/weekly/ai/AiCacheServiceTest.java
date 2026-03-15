package com.weekly.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void clearRemovesAllEntries() {
        AiCacheService cache = new AiCacheService(Duration.ofHours(1));
        cache.put("key1", "val1");
        cache.put("key2", "val2");
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
    }
}
