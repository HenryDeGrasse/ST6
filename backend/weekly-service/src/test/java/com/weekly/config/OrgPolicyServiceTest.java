package com.weekly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CachingOrgPolicyService}: default fallback and cache behaviour.
 */
class OrgPolicyServiceTest {

    private OrgPolicyRepository repository;
    private OrgPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(OrgPolicyRepository.class);
        service = new CachingOrgPolicyService(repository);
    }

    // ── Default fallback ─────────────────────────────────────

    @Nested
    class DefaultFallback {

        @Test
        void returnsSensibleDefaultsWhenNoPolicyRow() {
            UUID orgId = UUID.randomUUID();
            when(repository.findByOrgId(orgId)).thenReturn(Optional.empty());

            OrgPolicyService.OrgPolicy policy = service.getPolicy(orgId);

            assertNotNull(policy);
            assertEquals(1, policy.chessMaxKing());
            assertEquals(2, policy.chessMaxQueen());
            assertEquals(60, policy.rcdoStalenessThresholdMinutes());
            assertEquals(true, policy.chessKingRequired());
            assertEquals(true, policy.blockLockOnStaleRcdo());
        }

        @Test
        void defaultsMatchOrgPolicyServiceDefaultPolicy() {
            UUID orgId = UUID.randomUUID();
            when(repository.findByOrgId(orgId)).thenReturn(Optional.empty());

            OrgPolicyService.OrgPolicy fromService = service.getPolicy(orgId);
            OrgPolicyService.OrgPolicy defaultPolicy = OrgPolicyService.defaultPolicy();

            assertEquals(defaultPolicy.chessMaxKing(), fromService.chessMaxKing());
            assertEquals(defaultPolicy.chessMaxQueen(), fromService.chessMaxQueen());
            assertEquals(defaultPolicy.rcdoStalenessThresholdMinutes(),
                    fromService.rcdoStalenessThresholdMinutes());
            assertEquals(defaultPolicy.lockDay(), fromService.lockDay());
            assertEquals(defaultPolicy.lockTime(), fromService.lockTime());
            assertEquals(defaultPolicy.reconcileDay(), fromService.reconcileDay());
            assertEquals(defaultPolicy.reconcileTime(), fromService.reconcileTime());
        }

        @Test
        void defaultPolicyHasExpectedValues() {
            OrgPolicyService.OrgPolicy defaults = OrgPolicyService.defaultPolicy();

            assertEquals(1, defaults.chessMaxKing());
            assertEquals(2, defaults.chessMaxQueen());
            assertEquals(60, defaults.rcdoStalenessThresholdMinutes());
            assertEquals("MONDAY", defaults.lockDay());
            assertEquals("10:00", defaults.lockTime());
            assertEquals("FRIDAY", defaults.reconcileDay());
            assertEquals("16:00", defaults.reconcileTime());
            assertEquals(true, defaults.chessKingRequired());
            assertEquals(true, defaults.blockLockOnStaleRcdo());
        }

        @Test
        void defaultPolicyHasExpectedDigestSchedule() {
            OrgPolicyService.OrgPolicy defaults = OrgPolicyService.defaultPolicy();

            assertEquals("FRIDAY", defaults.digestDay());
            assertEquals("17:00", defaults.digestTime());
        }
    }

    // ── DB row loaded ────────────────────────────────────────

    @Nested
    class PolicyFromDatabase {

        @Test
        void returnsValuesFromEntityWhenRowExists() {
            UUID orgId = UUID.randomUUID();
            OrgPolicyEntity entity = buildEntity(orgId, false, 2, 3, "TUESDAY", "09:00",
                    "THURSDAY", "15:00", false, 120);
            when(repository.findByOrgId(orgId)).thenReturn(Optional.of(entity));

            OrgPolicyService.OrgPolicy policy = service.getPolicy(orgId);

            assertEquals(false, policy.chessKingRequired());
            assertEquals(2, policy.chessMaxKing());
            assertEquals(3, policy.chessMaxQueen());
            assertEquals("TUESDAY", policy.lockDay());
            assertEquals("09:00", policy.lockTime());
            assertEquals("THURSDAY", policy.reconcileDay());
            assertEquals("15:00", policy.reconcileTime());
            assertEquals(false, policy.blockLockOnStaleRcdo());
            assertEquals(120, policy.rcdoStalenessThresholdMinutes());
        }
    }

    // ── Cache behaviour ──────────────────────────────────────

    @Nested
    class CacheBehaviour {

        @Test
        void dbIsOnlyQueriedOnceForSameOrg() {
            UUID orgId = UUID.randomUUID();
            when(repository.findByOrgId(orgId)).thenReturn(Optional.empty());

            OrgPolicyService.OrgPolicy first = service.getPolicy(orgId);
            OrgPolicyService.OrgPolicy second = service.getPolicy(orgId);

            // Repository must only be called once — subsequent calls return cached value
            verify(repository, times(1)).findByOrgId(orgId);
            assertSame(first, second);
        }

        @Test
        void differentOrgsHaveIndependentCacheEntries() {
            UUID orgA = UUID.randomUUID();
            UUID orgB = UUID.randomUUID();
            OrgPolicyEntity entityA = buildEntity(orgA, true, 1, 2, "MONDAY", "10:00",
                    "FRIDAY", "16:00", true, 60);
            OrgPolicyEntity entityB = buildEntity(orgB, false, 2, 4, "WEDNESDAY", "08:00",
                    "WEDNESDAY", "18:00", false, 30);
            when(repository.findByOrgId(orgA)).thenReturn(Optional.of(entityA));
            when(repository.findByOrgId(orgB)).thenReturn(Optional.of(entityB));

            OrgPolicyService.OrgPolicy policyA = service.getPolicy(orgA);
            OrgPolicyService.OrgPolicy policyB = service.getPolicy(orgB);

            assertEquals(60, policyA.rcdoStalenessThresholdMinutes());
            assertEquals(30, policyB.rcdoStalenessThresholdMinutes());
        }

        @Test
        void evictForcesDbReloadOnNextAccess() {
            UUID orgId = UUID.randomUUID();
            when(repository.findByOrgId(orgId)).thenReturn(Optional.empty());

            service.getPolicy(orgId); // prime the cache
            service.evict(orgId);
            service.getPolicy(orgId); // should reload from DB

            verify(repository, times(2)).findByOrgId(orgId);
        }
    }

    // ── Digest config fields ─────────────────────────────────

    @Nested
    class DigestConfigFields {

        @Test
        void entityDigestFieldsAreUsedWhenPresent() {
            UUID orgId = UUID.randomUUID();
            OrgPolicyEntity entity = buildEntityWithDigest(
                    orgId, true, 1, 2, "MONDAY", "10:00", "FRIDAY", "16:00", true, 60, "WEDNESDAY", "12:00"
            );
            when(repository.findByOrgId(orgId)).thenReturn(Optional.of(entity));

            OrgPolicyService.OrgPolicy policy = service.getPolicy(orgId);

            assertEquals("WEDNESDAY", policy.digestDay());
            assertEquals("12:00", policy.digestTime());
        }

        @Test
        void nullEntityDigestFieldsFallBackToDefaults() {
            UUID orgId = UUID.randomUUID();
            OrgPolicyEntity entity = buildEntity(orgId, true, 1, 2, "MONDAY", "10:00",
                    "FRIDAY", "16:00", true, 60);
            // digestDay / digestTime fields are null (legacy row)
            when(repository.findByOrgId(orgId)).thenReturn(Optional.of(entity));

            OrgPolicyService.OrgPolicy policy = service.getPolicy(orgId);

            assertEquals("FRIDAY", policy.digestDay());
            assertEquals("17:00", policy.digestTime());
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Creates an {@link OrgPolicyEntity} via reflection since the JPA constructor is protected.
     */
    private static OrgPolicyEntity buildEntity(
            UUID orgId, boolean chessKingRequired, int chessMaxKing, int chessMaxQueen,
            String lockDay, String lockTime, String reconcileDay, String reconcileTime,
            boolean blockLockOnStaleRcdo, int rcdoStalenessThresholdMinutes
    ) {
        try {
            var constructor = OrgPolicyEntity.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            OrgPolicyEntity entity = (OrgPolicyEntity) constructor.newInstance();

            setField(entity, "orgId", orgId);
            setField(entity, "chessKingRequired", chessKingRequired);
            setField(entity, "chessMaxKing", chessMaxKing);
            setField(entity, "chessMaxQueen", chessMaxQueen);
            setField(entity, "lockDay", lockDay);
            setField(entity, "lockTime", lockTime);
            setField(entity, "reconcileDay", reconcileDay);
            setField(entity, "reconcileTime", reconcileTime);
            setField(entity, "blockLockOnStaleRcdo", blockLockOnStaleRcdo);
            setField(entity, "rcdoStalenessThresholdMinutes", rcdoStalenessThresholdMinutes);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OrgPolicyEntity for test", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = OrgPolicyEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Creates an {@link OrgPolicyEntity} with digest fields set.
     */
    private static OrgPolicyEntity buildEntityWithDigest(
            UUID orgId, boolean chessKingRequired, int chessMaxKing, int chessMaxQueen,
            String lockDay, String lockTime, String reconcileDay, String reconcileTime,
            boolean blockLockOnStaleRcdo, int rcdoStalenessThresholdMinutes,
            String digestDay, String digestTime
    ) {
        try {
            OrgPolicyEntity entity = buildEntity(orgId, chessKingRequired, chessMaxKing,
                    chessMaxQueen, lockDay, lockTime, reconcileDay, reconcileTime,
                    blockLockOnStaleRcdo, rcdoStalenessThresholdMinutes);
            setField(entity, "digestDay", digestDay);
            setField(entity, "digestTime", digestTime);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build OrgPolicyEntity with digest fields", e);
        }
    }
}
