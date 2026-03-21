package com.weekly.rcdo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caffeine-backed LRU fallback decorator for {@link RcdoClient} (PRD §9.2).
 *
 * <p>Wraps a primary {@link RcdoClient} and adds an in-process Caffeine
 * cache (maxSize=100, TTL=60 s) as a degradation path. On {@link #getTree}:
 * <ol>
 *   <li>Calls the delegate (Redis-backed or in-memory).</li>
 *   <li>On success: writes the result through to the Caffeine cache and
 *       resets the {@code rcdo_cache_fallback_active} gauge to 0.</li>
 *   <li>On delegate failure: checks the Caffeine cache.
 *       If a cached entry exists, returns it and sets the gauge to 1.
 *       If the cache is cold, rethrows the original exception.</li>
 * </ol>
 *
 * <p>Search and single-outcome lookups are not cached separately; on delegate
 * failure they are derived from the cached {@link RcdoTree} snapshot.
 */
public class CachingRcdoClientDecorator implements RcdoClient {

    static final int MAX_SIZE = 100;
    static final long TTL_SECONDS = 60L;

    private final RcdoClient delegate;
    private final Cache<UUID, RcdoTree> treeCache;
    private final AtomicLong fallbackActiveRef;

    public CachingRcdoClientDecorator(RcdoClient delegate, MeterRegistry meterRegistry) {
        this(delegate, meterRegistry,
                Caffeine.newBuilder()
                        .maximumSize(MAX_SIZE)
                        .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
                        .build());
    }

    /**
     * Package-private constructor for testing with a custom Caffeine cache
     * (e.g. one backed by a {@link com.github.benmanes.caffeine.cache.Ticker}
     * that can be advanced manually to simulate TTL expiry).
     */
    CachingRcdoClientDecorator(RcdoClient delegate, MeterRegistry meterRegistry,
            Cache<UUID, RcdoTree> cache) {
        this.delegate = delegate;
        this.treeCache = cache;
        this.fallbackActiveRef = meterRegistry.gauge(
                "rcdo_cache_fallback_active",
                new AtomicLong(0L));
    }

    /**
     * Returns the underlying delegate so callers (e.g. {@code RcdoController})
     * can still perform {@code instanceof} checks on the original type.
     */
    public RcdoClient getDelegate() {
        return delegate;
    }

    @Override
    public RcdoTree getTree(UUID orgId) {
        try {
            RcdoTree tree = delegate.getTree(orgId);
            treeCache.put(orgId, tree);
            markFallbackInactive();
            return tree;
        } catch (Exception e) {
            Optional<RcdoTree> fallbackTree = getFallbackTree(orgId);
            if (fallbackTree.isPresent()) {
                return fallbackTree.get();
            }
            throw e;
        }
    }

    @Override
    public List<RcdoSearchResult> search(UUID orgId, String query) {
        try {
            List<RcdoSearchResult> results = delegate.search(orgId, query);
            markFallbackInactive();
            return results;
        } catch (Exception e) {
            Optional<RcdoTree> fallbackTree = getFallbackTree(orgId);
            if (fallbackTree.isPresent()) {
                return searchTree(fallbackTree.get(), query);
            }
            throw e;
        }
    }

    @Override
    public Optional<RcdoOutcomeDetail> getOutcome(UUID orgId, UUID outcomeId) {
        try {
            Optional<RcdoOutcomeDetail> outcome = delegate.getOutcome(orgId, outcomeId);
            markFallbackInactive();
            return outcome;
        } catch (Exception e) {
            Optional<RcdoTree> fallbackTree = getFallbackTree(orgId);
            if (fallbackTree.isPresent()) {
                return getOutcomeFromTree(fallbackTree.get(), outcomeId);
            }
            throw e;
        }
    }

    @Override
    public boolean isCacheFresh(UUID orgId, int stalenessThresholdMin) {
        return delegate.isCacheFresh(orgId, stalenessThresholdMin);
    }

    @Override
    public Instant getLastRefreshedAt(UUID orgId) {
        return delegate.getLastRefreshedAt(orgId);
    }

    private void markFallbackInactive() {
        fallbackActiveRef.set(0L);
    }

    private Optional<RcdoTree> getFallbackTree(UUID orgId) {
        RcdoTree cached = treeCache.getIfPresent(orgId);
        if (cached != null) {
            fallbackActiveRef.set(1L);
            return Optional.of(cached);
        }
        markFallbackInactive();
        return Optional.empty();
    }

    private List<RcdoSearchResult> searchTree(RcdoTree tree, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return tree.rallyCries().stream()
                .flatMap(rallyCry -> rallyCry.objectives().stream()
                        .flatMap(objective -> objective.outcomes().stream()
                                .filter(outcome -> outcome.name()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(lowerQuery))
                                .map(outcome -> new RcdoSearchResult(
                                        outcome.id(), outcome.name(),
                                        objective.id(), objective.name(),
                                        rallyCry.id(), rallyCry.name()
                                ))))
                .toList();
    }

    private Optional<RcdoOutcomeDetail> getOutcomeFromTree(RcdoTree tree, UUID outcomeId) {
        for (RcdoTree.RallyCry rallyCry : tree.rallyCries()) {
            for (RcdoTree.Objective objective : rallyCry.objectives()) {
                for (RcdoTree.Outcome outcome : objective.outcomes()) {
                    if (outcome.id().equals(outcomeId.toString())) {
                        return Optional.of(new RcdoOutcomeDetail(
                                outcome.id(), outcome.name(),
                                objective.id(), objective.name(),
                                rallyCry.id(), rallyCry.name()
                        ));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
