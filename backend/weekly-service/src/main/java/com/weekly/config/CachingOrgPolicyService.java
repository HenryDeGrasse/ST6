package com.weekly.config;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link OrgPolicyService} that caches results in an
 * in-process {@link ConcurrentHashMap}.
 *
 * <p>Policies are loaded from {@link OrgPolicyRepository} on first access per org
 * and held in memory thereafter (policies change rarely — admin-only mutations).
 * Call {@link #evict(UUID)} after an org policy is mutated to invalidate the entry.
 */
@Service
public class CachingOrgPolicyService implements OrgPolicyService {

    private final OrgPolicyRepository repository;
    private final ConcurrentHashMap<UUID, OrgPolicyService.OrgPolicy> cache =
            new ConcurrentHashMap<>();

    public CachingOrgPolicyService(OrgPolicyRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrgPolicyService.OrgPolicy getPolicy(UUID orgId) {
        return cache.computeIfAbsent(orgId, id ->
                repository.findByOrgId(id)
                        .map(CachingOrgPolicyService::fromEntity)
                        .orElseGet(OrgPolicyService::defaultPolicy)
        );
    }

    @Override
    public void evict(UUID orgId) {
        cache.remove(orgId);
    }

    /**
     * Persists the updated digest schedule to the database.
     *
     * <p>If no policy row exists for the org, a new row is inserted using the
     * system defaults with the requested digest schedule overridden. The in-memory
     * cache is NOT evicted here — callers should invoke {@link #evict(UUID)} after
     * calling this method.
     *
     * @param orgId      the org to update
     * @param digestDay  day-of-week string (e.g. "FRIDAY")
     * @param digestTime HH:mm time string (e.g. "17:00")
     */
    @Override
    @Transactional
    public void updateDigestConfig(UUID orgId, String digestDay, String digestTime) {
        OrgPolicyEntity entity = repository.findByOrgId(orgId).orElseGet(() -> {
            OrgPolicyEntity newEntity = buildDefaultEntity(orgId);
            return repository.save(newEntity);
        });
        entity.updateDigestConfig(digestDay, digestTime);
        repository.save(entity);
    }

    // ── Entity factory ───────────────────────────────────────

    /**
     * Creates a new {@link OrgPolicyEntity} seeded with the system defaults for an org
     * that has no existing policy row. This occurs when {@code updateDigestConfig} is
     * called before any policy row has been provisioned.
     */
    private static OrgPolicyEntity buildDefaultEntity(UUID orgId) {
        try {
            var constructor = OrgPolicyEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            OrgPolicyEntity entity = (OrgPolicyEntity) constructor.newInstance();

            setEntityField(entity, "orgId", orgId);
            setEntityField(entity, "chessKingRequired", true);
            setEntityField(entity, "chessMaxKing", 1);
            setEntityField(entity, "chessMaxQueen", 2);
            setEntityField(entity, "lockDay", "MONDAY");
            setEntityField(entity, "lockTime", "10:00");
            setEntityField(entity, "reconcileDay", "FRIDAY");
            setEntityField(entity, "reconcileTime", "16:00");
            setEntityField(entity, "blockLockOnStaleRcdo", true);
            setEntityField(entity, "rcdoStalenessThresholdMinutes", 60);
            setEntityField(entity, "digestDay", "FRIDAY");
            setEntityField(entity, "digestTime", "17:00");
            setEntityField(entity, "createdAt", java.time.Instant.now());
            setEntityField(entity, "updatedAt", java.time.Instant.now());

            return entity;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build default OrgPolicyEntity", e);
        }
    }

    private static void setEntityField(Object target, String fieldName, Object value) throws Exception {
        var field = OrgPolicyEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ── Conversion ───────────────────────────────────────────

    private static OrgPolicyService.OrgPolicy fromEntity(OrgPolicyEntity entity) {
        // digestDay/digestTime may be null for legacy rows created before V5 migration;
        // fall back to defaults in that case.
        String digestDay = entity.getDigestDay() != null ? entity.getDigestDay() : "FRIDAY";
        String digestTime = entity.getDigestTime() != null ? entity.getDigestTime() : "17:00";
        return new OrgPolicyService.OrgPolicy(
                entity.isChessKingRequired(),
                entity.getChessMaxKing(),
                entity.getChessMaxQueen(),
                entity.getLockDay(),
                entity.getLockTime(),
                entity.getReconcileDay(),
                entity.getReconcileTime(),
                entity.isBlockLockOnStaleRcdo(),
                entity.getRcdoStalenessThresholdMinutes(),
                digestDay,
                digestTime
        );
    }
}
