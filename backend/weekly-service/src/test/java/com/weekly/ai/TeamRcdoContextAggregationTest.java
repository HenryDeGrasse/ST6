package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.PlanTeamRcdoUsageProvider;
import com.weekly.shared.TeamRcdoUsageProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the team RCDO usage aggregation
 * ({@link PlanTeamRcdoUsageProvider}).
 *
 * <p>Uses mocked repositories but real entity instances to verify aggregation
 * logic without a real database (consistent with the project's existing test
 * strategy; entities are not Mockito-mockable on JDK 25).
 */
class TeamRcdoContextAggregationTest {

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private PlanTeamRcdoUsageProvider provider;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 9);

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        provider = new PlanTeamRcdoUsageProvider(planRepository, commitRepository);
    }

    @Test
    void returnsEmptyWhenNoPlansForWeek() {
        when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), eq(WEEK_START), eq(WEEK_START)))
                .thenReturn(List.of());
        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                eq(ORG_ID), eq(LocalDate.of(2026, 1, 1)), eq(WEEK_START)))
                .thenReturn(List.of());

        TeamRcdoUsageProvider.TeamRcdoUsageResult result = provider.getTeamRcdoUsage(ORG_ID, WEEK_START);

        assertTrue(result.outcomes().isEmpty(),
                "Should return empty outcome list when no plans exist for the week");
        assertTrue(result.coveredOutcomeIdsThisQuarter().isEmpty(),
                "Quarter coverage should also be empty when no plans exist in the quarter window");
    }

    @Test
    void aggregatesCommitCountsPerOutcome() {
        UUID planId1 = UUID.randomUUID();
        UUID planId2 = UUID.randomUUID();
        UUID outcomeA = UUID.randomUUID();
        UUID outcomeB = UUID.randomUUID();

        WeeklyPlanEntity plan1 = plan(planId1);
        WeeklyPlanEntity plan2 = plan(planId2);
        when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), eq(WEEK_START), eq(WEEK_START)))
                .thenReturn(List.of(plan1, plan2));

        // plan1 has 2 commits linked to outcomeA, plan2 has 1 commit linked to outcomeB
        WeeklyCommitEntity c1 = outcomeCommit(planId1, outcomeA, "Grow ARR");
        WeeklyCommitEntity c2 = outcomeCommit(planId1, outcomeA, "Grow ARR");
        WeeklyCommitEntity c3 = outcomeCommit(planId2, outcomeB, "Improve NPS");

        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(c1, c2, c3));

        TeamRcdoUsageProvider.TeamRcdoUsageResult result = provider.getTeamRcdoUsage(ORG_ID, WEEK_START);

        assertEquals(2, result.outcomes().size());
        // Sorted descending by commit count: outcomeA (2) first, outcomeB (1) second
        assertEquals(outcomeA.toString(), result.outcomes().get(0).outcomeId());
        assertEquals(2, result.outcomes().get(0).commitCount());
        assertEquals("Grow ARR", result.outcomes().get(0).outcomeName());

        assertEquals(outcomeB.toString(), result.outcomes().get(1).outcomeId());
        assertEquals(1, result.outcomes().get(1).commitCount());
        assertEquals("Improve NPS", result.outcomes().get(1).outcomeName());
    }

    @Test
    void skipsCommitsWithoutOutcomeId() {
        UUID planId = UUID.randomUUID();
        UUID outcomeA = UUID.randomUUID();

        WeeklyPlanEntity plan = plan(planId);
        when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), eq(WEEK_START), eq(WEEK_START)))
                .thenReturn(List.of(plan));

        WeeklyCommitEntity linked = outcomeCommit(planId, outcomeA, "Grow ARR");
        WeeklyCommitEntity unlinked = bareCommit(planId); // no outcomeId

        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(linked, unlinked));

        TeamRcdoUsageProvider.TeamRcdoUsageResult result = provider.getTeamRcdoUsage(ORG_ID, WEEK_START);

        assertEquals(1, result.outcomes().size(),
                "Commits without an outcome ID must be ignored");
        assertEquals(outcomeA.toString(), result.outcomes().get(0).outcomeId());
        assertEquals(1, result.outcomes().get(0).commitCount());
    }

    @Test
    void fallsBackToOutcomeIdAsNameWhenNoSnapshotName() {
        UUID planId = UUID.randomUUID();
        UUID outcomeA = UUID.randomUUID();

        WeeklyPlanEntity plan = plan(planId);
        when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), eq(WEEK_START), eq(WEEK_START)))
                .thenReturn(List.of(plan));

        // Commit with outcome but no snapshot name
        WeeklyCommitEntity commit = outcomeCommitNoSnapshot(planId, outcomeA);
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(commit));

        TeamRcdoUsageProvider.TeamRcdoUsageResult result = provider.getTeamRcdoUsage(ORG_ID, WEEK_START);

        assertEquals(1, result.outcomes().size());
        assertEquals(outcomeA.toString(), result.outcomes().get(0).outcomeName(),
                "When no snapshot name is available, outcomeId should be used as the name");
    }

    @Test
    void resultsAreSortedByCommitCountDescending() {
        UUID planId = UUID.randomUUID();
        UUID outcomeA = UUID.randomUUID();
        UUID outcomeB = UUID.randomUUID();
        UUID outcomeC = UUID.randomUUID();

        WeeklyPlanEntity plan = plan(planId);
        when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), eq(WEEK_START), eq(WEEK_START)))
                .thenReturn(List.of(plan));

        // outcomeB appears 3 times, outcomeC 2 times, outcomeA 1 time
        List<WeeklyCommitEntity> commits = List.of(
                outcomeCommit(planId, outcomeA, "Outcome A"),
                outcomeCommit(planId, outcomeB, "Outcome B"),
                outcomeCommit(planId, outcomeB, "Outcome B"),
                outcomeCommit(planId, outcomeB, "Outcome B"),
                outcomeCommit(planId, outcomeC, "Outcome C"),
                outcomeCommit(planId, outcomeC, "Outcome C")
        );
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(commits);

        TeamRcdoUsageProvider.TeamRcdoUsageResult result = provider.getTeamRcdoUsage(ORG_ID, WEEK_START);

        assertEquals(3, result.outcomes().size());
        assertEquals(3, result.outcomes().get(0).commitCount(), "Top outcome should have 3 commits");
        assertEquals(2, result.outcomes().get(1).commitCount(), "Second outcome should have 2 commits");
        assertEquals(1, result.outcomes().get(2).commitCount(), "Third outcome should have 1 commit");
    }

    @Test
    void preservesQuarterCoverageEvenWhenCurrentWeekHasNoUsage() {
        UUID priorQuarterPlanId = UUID.randomUUID();
        UUID coveredOutcome = UUID.randomUUID();
        LocalDate priorWeek = LocalDate.of(2026, 2, 2);

        when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), eq(WEEK_START), eq(WEEK_START)))
                .thenReturn(List.of());
        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                eq(ORG_ID), eq(LocalDate.of(2026, 1, 1)), eq(WEEK_START)))
                .thenReturn(List.of(plan(priorQuarterPlanId, priorWeek)));
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(outcomeCommit(priorQuarterPlanId, coveredOutcome, "Covered Earlier")));

        TeamRcdoUsageProvider.TeamRcdoUsageResult result = provider.getTeamRcdoUsage(ORG_ID, WEEK_START);

        assertTrue(result.outcomes().isEmpty(),
                "Current-week usage should be empty when the team has not linked outcomes this week");
        assertEquals(Set.of(coveredOutcome.toString()), result.coveredOutcomeIdsThisQuarter(),
                "Quarter coverage should still track earlier linked outcomes");
    }

    // ── Cache key tests ──────────────────────────────────────────────────────

    @Test
    void cacheKeyIsDeterministicPerOrgAndWeek() {
        UUID orgId = UUID.randomUUID();
        LocalDate week = LocalDate.of(2026, 3, 9);

        String key1 = AiCacheService.buildTeamRcdoUsageKey(orgId, week);
        String key2 = AiCacheService.buildTeamRcdoUsageKey(orgId, week);

        assertEquals(key1, key2, "Cache key must be deterministic for same org+week");
        assertTrue(key1.startsWith("ai:team-rcdo-usage:"), "Cache key should have correct prefix");
    }

    @Test
    void cacheKeyDiffersByOrg() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        LocalDate week = LocalDate.of(2026, 3, 9);

        String keyA = AiCacheService.buildTeamRcdoUsageKey(orgA, week);
        String keyB = AiCacheService.buildTeamRcdoUsageKey(orgB, week);

        assertFalse(keyA.equals(keyB), "Different orgs must not share the same team RCDO cache key");
    }

    @Test
    void cacheKeyDiffersByWeek() {
        UUID orgId = UUID.randomUUID();
        LocalDate week1 = LocalDate.of(2026, 3, 9);
        LocalDate week2 = LocalDate.of(2026, 3, 16);

        String key1 = AiCacheService.buildTeamRcdoUsageKey(orgId, week1);
        String key2 = AiCacheService.buildTeamRcdoUsageKey(orgId, week2);

        assertFalse(key1.equals(key2), "Different weeks must produce different team RCDO cache keys");
    }

    // ── Helper factories ─────────────────────────────────────────────────────

    private WeeklyPlanEntity plan(UUID planId) {
        return plan(planId, WEEK_START);
    }

    private WeeklyPlanEntity plan(UUID planId, LocalDate weekStart) {
        return new WeeklyPlanEntity(planId, ORG_ID, OWNER_ID, weekStart);
    }

    private WeeklyCommitEntity outcomeCommit(UUID planId, UUID outcomeId, String outcomeName) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Work item");
        commit.setOutcomeId(outcomeId);
        // populateSnapshot stores the outcome name under snapshotOutcomeName
        commit.populateSnapshot(null, null, null, null, outcomeId, outcomeName);
        return commit;
    }

    private WeeklyCommitEntity outcomeCommitNoSnapshot(UUID planId, UUID outcomeId) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Work item");
        commit.setOutcomeId(outcomeId);
        // No snapshot — snapshotOutcomeName remains null
        return commit;
    }

    private WeeklyCommitEntity bareCommit(UUID planId) {
        // No outcomeId — simulates a non-strategic commit
        return new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Non-strategic work");
    }
}
