package com.weekly.rcdo;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link RcdoClient} for development and testing.
 *
 * <p>In production, this would be replaced by a Redis-cached adapter
 * that calls the upstream PA RCDO service.
 */
@Component
public class InMemoryRcdoClient implements RcdoClient {

    private final Map<UUID, RcdoTree> treesByOrg = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastRefreshed = new ConcurrentHashMap<>();

    @Override
    public RcdoTree getTree(UUID orgId) {
        return treesByOrg.getOrDefault(orgId, new RcdoTree(List.of()));
    }

    @Override
    public List<RcdoSearchResult> search(UUID orgId, String query) {
        RcdoTree tree = getTree(orgId);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return tree.rallyCries().stream()
                .flatMap(rc -> rc.objectives().stream()
                        .flatMap(obj -> obj.outcomes().stream()
                                .filter(o -> o.name().toLowerCase(Locale.ROOT).contains(lowerQuery))
                                .map(o -> new RcdoSearchResult(
                                        o.id(), o.name(),
                                        obj.id(), obj.name(),
                                        rc.id(), rc.name()
                                ))
                        ))
                .toList();
    }

    @Override
    public Optional<RcdoOutcomeDetail> getOutcome(UUID orgId, UUID outcomeId) {
        RcdoTree tree = getTree(orgId);
        for (RcdoTree.RallyCry rc : tree.rallyCries()) {
            for (RcdoTree.Objective obj : rc.objectives()) {
                for (RcdoTree.Outcome o : obj.outcomes()) {
                    if (o.id().equals(outcomeId.toString())) {
                        return Optional.of(new RcdoOutcomeDetail(
                                o.id(), o.name(),
                                obj.id(), obj.name(),
                                rc.id(), rc.name()
                        ));
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isCacheFresh(UUID orgId, int stalenessThresholdMin) {
        Instant refreshed = lastRefreshed.get(orgId);
        if (refreshed == null) {
            return true; // Assume fresh for in-memory development
        }
        return Instant.now().minusSeconds((long) stalenessThresholdMin * 60).isBefore(refreshed);
    }

    @Override
    public Instant getLastRefreshedAt(UUID orgId) {
        return lastRefreshed.get(orgId);
    }

    // ── Test helpers ─────────────────────────────────────────

    /**
     * Sets the RCDO tree for an org (used in tests).
     */
    public void setTree(UUID orgId, RcdoTree tree) {
        treesByOrg.put(orgId, tree);
        lastRefreshed.put(orgId, Instant.now());
    }

    /**
     * Clears all stored data (used in tests).
     */
    public void clear() {
        treesByOrg.clear();
        lastRefreshed.clear();
    }

    /**
     * Forces the RCDO cache for the given org to appear stale (older than the
     * default 60-minute staleness threshold). Used in integration tests that
     * exercise the {@code RCDO_VALIDATION_STALE} lock-rejection path.
     */
    public void markStale(UUID orgId) {
        // 90 minutes in the past — safely beyond the 60-minute threshold
        lastRefreshed.put(orgId, Instant.now().minusSeconds(90L * 60));
    }
}
