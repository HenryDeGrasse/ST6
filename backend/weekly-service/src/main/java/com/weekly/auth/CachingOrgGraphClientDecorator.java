package com.weekly.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Caffeine-backed LRU fallback decorator for {@link OrgGraphClient} (PRD §9.2).
 *
 * <p>Wraps a primary {@link OrgGraphClient} and adds an in-process Caffeine
 * cache (maxSize=100, TTL=60 s) as a degradation path. Direct-report IDs and
 * display-name payloads are cached separately so the team dashboard can retain
 * human-readable names during fallback.
 */
public class CachingOrgGraphClientDecorator implements OrgGraphClient {

    static final int MAX_SIZE = 100;
    static final long TTL_SECONDS = 60L;

    private final OrgGraphClient delegate;
    private final Cache<String, List<UUID>> directReportsCache;
    private final Cache<String, List<DirectReport>> directReportsWithNamesCache;

    public CachingOrgGraphClientDecorator(OrgGraphClient delegate) {
        this(
                delegate,
                Caffeine.newBuilder()
                        .maximumSize(MAX_SIZE)
                        .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
                        .build(),
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
    CachingOrgGraphClientDecorator(OrgGraphClient delegate, Cache<String, List<UUID>> cache) {
        this(
                delegate,
                cache,
                Caffeine.newBuilder()
                        .maximumSize(MAX_SIZE)
                        .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
                        .build());
    }

    CachingOrgGraphClientDecorator(
            OrgGraphClient delegate,
            Cache<String, List<UUID>> directReportsCache,
            Cache<String, List<DirectReport>> directReportsWithNamesCache) {
        this.delegate = delegate;
        this.directReportsCache = directReportsCache;
        this.directReportsWithNamesCache = directReportsWithNamesCache;
    }

    /**
     * Returns the underlying delegate so callers can perform {@code instanceof}
     * checks on the original type if needed.
     */
    public OrgGraphClient getDelegate() {
        return delegate;
    }

    @Override
    public List<UUID> getDirectReports(UUID orgId, UUID managerId) {
        String key = cacheKey(orgId, managerId);
        try {
            List<UUID> reports = List.copyOf(delegate.getDirectReports(orgId, managerId));
            directReportsCache.put(key, reports);
            return reports;
        } catch (Exception e) {
            List<UUID> cached = directReportsCache.getIfPresent(key);
            if (cached != null) {
                return cached;
            }
            throw e;
        }
    }

    @Override
    public List<DirectReport> getDirectReportsWithNames(UUID orgId, UUID managerId) {
        String key = cacheKey(orgId, managerId);
        try {
            List<DirectReport> reports = List.copyOf(delegate.getDirectReportsWithNames(orgId, managerId));
            directReportsWithNamesCache.put(key, reports);
            directReportsCache.put(key, reports.stream().map(DirectReport::userId).toList());
            return reports;
        } catch (Exception e) {
            List<DirectReport> cachedWithNames = directReportsWithNamesCache.getIfPresent(key);
            if (cachedWithNames != null) {
                return cachedWithNames;
            }

            List<UUID> cachedReports = directReportsCache.getIfPresent(key);
            if (cachedReports != null) {
                return cachedReports.stream()
                        .map(reportId -> new DirectReport(reportId, reportId.toString()))
                        .toList();
            }
            throw e;
        }
    }

    private String cacheKey(UUID orgId, UUID managerId) {
        return orgId + ":" + managerId;
    }
}
