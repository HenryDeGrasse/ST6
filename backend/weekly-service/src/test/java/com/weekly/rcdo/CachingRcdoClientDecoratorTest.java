package com.weekly.rcdo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CachingRcdoClientDecorator}.
 *
 * <p>Covers: write-through on success, fallback on delegate failure,
 * rethrow when cache is cold, TTL expiry, and metric gauge transitions.
 */
class CachingRcdoClientDecoratorTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OUTCOME_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final RcdoTree TREE = new RcdoTree(List.of());
    private static final RcdoTree TREE_WITH_OUTCOME = new RcdoTree(List.of(
            new RcdoTree.RallyCry(
                    "rc-1",
                    "Grow revenue",
                    List.of(new RcdoTree.Objective(
                            "obj-1",
                            "Expand pipeline",
                            "rc-1",
                            List.of(new RcdoTree.Outcome(
                                    OUTCOME_ID.toString(),
                                    "Expand key accounts",
                                    "obj-1"
                            ))
                    ))
            )
    ));

    private RcdoClient delegate;
    private SimpleMeterRegistry registry;
    private CachingRcdoClientDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(RcdoClient.class);
        registry = new SimpleMeterRegistry();
        decorator = new CachingRcdoClientDecorator(delegate, registry);
    }

    // ── getDelegate ──────────────────────────────────────────────────────────

    @Test
    void getDelegateReturnsWrappedClient() {
        assertSame(delegate, decorator.getDelegate());
    }

    // ── write-through on success ─────────────────────────────────────────────

    @Nested
    class WriteThroughOnSuccess {

        @Test
        void getTreeDelegateSucceedsReturnsTree() {
            when(delegate.getTree(ORG_ID)).thenReturn(TREE);

            RcdoTree result = decorator.getTree(ORG_ID);

            assertSame(TREE, result);
            verify(delegate, times(1)).getTree(ORG_ID);
        }

        @Test
        void getTreeSuccessSetsGaugeToZero() {
            when(delegate.getTree(ORG_ID)).thenReturn(TREE);

            decorator.getTree(ORG_ID);

            double gauge = registry.get("rcdo_cache_fallback_active").gauge().value();
            assertEquals(0.0, gauge);
        }

        @Test
        void getTreeCalledTwiceForwardsBothCallsToDelegate() {
            // The Caffeine cache is a write-through fallback cache; on a delegate
            // success the result is cached but the next call still goes to the delegate.
            when(delegate.getTree(ORG_ID)).thenReturn(TREE);

            decorator.getTree(ORG_ID);
            decorator.getTree(ORG_ID);

            verify(delegate, times(2)).getTree(ORG_ID);
        }
    }

    // ── fallback on delegate failure ─────────────────────────────────────────

    @Nested
    class FallbackOnDelegateFailure {

        @Test
        void getTreeDelegateFailsReturnsCachedTreeOnHit() {
            // Seed the cache via a successful call first.
            when(delegate.getTree(ORG_ID)).thenReturn(TREE);
            decorator.getTree(ORG_ID);

            // Now make the delegate fail.
            when(delegate.getTree(ORG_ID)).thenThrow(new RuntimeException("Redis down"));

            RcdoTree result = decorator.getTree(ORG_ID);

            assertSame(TREE, result);
        }

        @Test
        void getTreeDelegateFailsSetsGaugeToOneOnCacheHit() {
            when(delegate.getTree(ORG_ID)).thenReturn(TREE);
            decorator.getTree(ORG_ID);

            when(delegate.getTree(ORG_ID)).thenThrow(new RuntimeException("Redis down"));
            decorator.getTree(ORG_ID);

            double gauge = registry.get("rcdo_cache_fallback_active").gauge().value();
            assertEquals(1.0, gauge);
        }

        @Test
        void getTreeGaugeResetsToZeroWhenDelegateRecovers() {
            // Seed, then fail, then recover.
            // Use doReturn/doThrow to avoid Mockito's re-stubbing problem: calling
            // when(mock.method()).thenReturn() after thenThrow would trigger the
            // configured exception during the when(...) capture call.
            doReturn(TREE).when(delegate).getTree(ORG_ID);
            decorator.getTree(ORG_ID); // seeds cache

            doThrow(new RuntimeException("Redis down")).when(delegate).getTree(ORG_ID);
            decorator.getTree(ORG_ID); // falls back → gauge = 1

            doReturn(TREE).when(delegate).getTree(ORG_ID);
            decorator.getTree(ORG_ID); // delegate recovers → gauge = 0

            double gauge = registry.get("rcdo_cache_fallback_active").gauge().value();
            assertEquals(0.0, gauge);
        }

        @Test
        void getTreeDelegateFailsRethrowsWhenCacheIsCold() {
            when(delegate.getTree(ORG_ID)).thenThrow(new RuntimeException("Redis down"));

            assertThrows(RuntimeException.class, () -> decorator.getTree(ORG_ID));
        }

        @Test
        void searchDelegateFailsReturnsResultsDerivedFromCachedTree() {
            when(delegate.getTree(ORG_ID)).thenReturn(TREE_WITH_OUTCOME);
            decorator.getTree(ORG_ID);

            when(delegate.search(ORG_ID, "expand"))
                    .thenThrow(new RuntimeException("Redis down"));

            assertEquals(List.of(new RcdoSearchResult(
                    OUTCOME_ID.toString(),
                    "Expand key accounts",
                    "obj-1",
                    "Expand pipeline",
                    "rc-1",
                    "Grow revenue"
            )), decorator.search(ORG_ID, "expand"));
        }

        @Test
        void getOutcomeDelegateFailsReturnsOutcomeDerivedFromCachedTree() {
            when(delegate.getTree(ORG_ID)).thenReturn(TREE_WITH_OUTCOME);
            decorator.getTree(ORG_ID);

            when(delegate.getOutcome(ORG_ID, OUTCOME_ID))
                    .thenThrow(new RuntimeException("Redis down"));

            assertEquals(Optional.of(new RcdoOutcomeDetail(
                    OUTCOME_ID.toString(),
                    "Expand key accounts",
                    "obj-1",
                    "Expand pipeline",
                    "rc-1",
                    "Grow revenue"
            )), decorator.getOutcome(ORG_ID, OUTCOME_ID));
        }
    }

    // ── TTL expiry ───────────────────────────────────────────────────────────

    @Nested
    class TtlExpiry {

        @Test
        void getTreeRethrowsAfterCacheExpiryAndResetsGaugeToZero() {
            // Use a manual ticker so we can advance time without sleeping.
            AtomicLong nanoTime = new AtomicLong(0L);
            Ticker ticker = nanoTime::get;
            SimpleMeterRegistry timedRegistry = new SimpleMeterRegistry();

            Cache<UUID, RcdoTree> timedCache = Caffeine.newBuilder()
                    .maximumSize(CachingRcdoClientDecorator.MAX_SIZE)
                    .expireAfterWrite(Duration.ofSeconds(CachingRcdoClientDecorator.TTL_SECONDS))
                    .ticker(ticker)
                    .build();

            CachingRcdoClientDecorator timedDecorator =
                    new CachingRcdoClientDecorator(delegate, timedRegistry, timedCache);

            // Seed the cache while delegate works.
            when(delegate.getTree(ORG_ID)).thenReturn(TREE);
            timedDecorator.getTree(ORG_ID);

            // Serve one fallback hit first so the gauge becomes 1.
            when(delegate.getTree(ORG_ID)).thenThrow(new RuntimeException("Redis down"));
            timedDecorator.getTree(ORG_ID);

            // Advance time past the 60 s TTL; the cached entry should now be expired.
            nanoTime.set(Duration.ofSeconds(61L).toNanos());

            assertThrows(RuntimeException.class, () -> timedDecorator.getTree(ORG_ID));
            assertEquals(0.0, timedRegistry.get("rcdo_cache_fallback_active").gauge().value());
        }

        @Test
        void getTreeReturnsCachedTreeWhenDelegateFailsWithinTtl() {
            AtomicLong nanoTime = new AtomicLong(0L);
            Ticker ticker = nanoTime::get;

            Cache<UUID, RcdoTree> timedCache = Caffeine.newBuilder()
                    .maximumSize(CachingRcdoClientDecorator.MAX_SIZE)
                    .expireAfterWrite(Duration.ofSeconds(CachingRcdoClientDecorator.TTL_SECONDS))
                    .ticker(ticker)
                    .build();

            CachingRcdoClientDecorator timedDecorator =
                    new CachingRcdoClientDecorator(delegate, registry, timedCache);

            when(delegate.getTree(ORG_ID)).thenReturn(TREE);
            timedDecorator.getTree(ORG_ID);

            // Advance time to just before TTL expiry.
            nanoTime.set(Duration.ofSeconds(59L).toNanos());

            when(delegate.getTree(ORG_ID)).thenThrow(new RuntimeException("Redis down"));

            RcdoTree result = timedDecorator.getTree(ORG_ID);
            assertSame(TREE, result);
        }
    }

    // ── delegate passthrough ─────────────────────────────────────────────────

    @Nested
    class DelegatePassthrough {

        @Test
        void searchDelegatesToWrappedClient() {
            when(delegate.search(ORG_ID, "foo")).thenReturn(List.of());

            decorator.search(ORG_ID, "foo");

            verify(delegate, times(1)).search(ORG_ID, "foo");
        }

        @Test
        void getOutcomeDelegatesToWrappedClient() {
            UUID outcomeId = UUID.randomUUID();
            when(delegate.getOutcome(ORG_ID, outcomeId)).thenReturn(Optional.empty());

            decorator.getOutcome(ORG_ID, outcomeId);

            verify(delegate, times(1)).getOutcome(ORG_ID, outcomeId);
        }

        @Test
        void isCacheFreshDelegatesToWrappedClient() {
            when(delegate.isCacheFresh(ORG_ID, 60)).thenReturn(true);

            boolean result = decorator.isCacheFresh(ORG_ID, 60);

            assertEquals(true, result);
            verify(delegate, times(1)).isCacheFresh(ORG_ID, 60);
        }

        @Test
        void getLastRefreshedAtDelegatesToWrappedClient() {
            when(delegate.getLastRefreshedAt(ORG_ID)).thenReturn(null);

            decorator.getLastRefreshedAt(ORG_ID);

            verify(delegate, times(1)).getLastRefreshedAt(ORG_ID);
        }
    }
}
