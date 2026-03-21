package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.PlanQualityDataProvider.CommitQualitySummary;
import com.weekly.shared.PlanQualityDataProvider.PlanQualityContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlanCommitQualityDataProvider}.
 */
class PlanCommitQualityDataProviderTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START =
            LocalDate.now().with(DayOfWeek.MONDAY);

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private PlanCommitQualityDataProvider provider;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        provider = new PlanCommitQualityDataProvider(planRepository, commitRepository);
    }

    private WeeklyPlanEntity makePlan(UUID planId, LocalDate weekStart) {
        return new WeeklyPlanEntity(planId, ORG_ID, USER_ID, weekStart);
    }

    private WeeklyCommitEntity makeCommit(UUID planId, CommitCategory category,
            ChessPriority priority, UUID outcomeId) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Test commit");
        commit.setCategory(category);
        commit.setChessPriority(priority);
        commit.setOutcomeId(outcomeId);
        return commit;
    }

    // ── getPlanQualityContext ─────────────────────────────────────────────────

    @Nested
    class GetPlanQualityContext {

        @Test
        void returnsEmptyContextWhenPlanNotFound() {
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.empty());

            PlanQualityContext result = provider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID);

            assertFalse(result.planFound());
            assertNull(result.weekStart());
            assertTrue(result.commits().isEmpty());
        }

        @Test
        void returnsEmptyContextWhenPlanBelongsToDifferentUser() {
            WeeklyPlanEntity otherUsersPlan = new WeeklyPlanEntity(
                    PLAN_ID, ORG_ID, UUID.randomUUID(), WEEK_START);
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(otherUsersPlan));

            PlanQualityContext result = provider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID);

            assertFalse(result.planFound());
            assertNull(result.weekStart());
            assertTrue(result.commits().isEmpty());
            verify(commitRepository, never()).findByOrgIdAndWeeklyPlanId(any(), any());
        }

        @Test
        void returnsPlanContextWithCommitSummaries() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            UUID outcomeId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(
                    PLAN_ID, CommitCategory.DELIVERY, ChessPriority.KING, outcomeId);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));

            PlanQualityContext result = provider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID);

            assertTrue(result.planFound());
            assertEquals(WEEK_START.toString(), result.weekStart());
            assertEquals(1, result.commits().size());

            CommitQualitySummary summary = result.commits().get(0);
            assertEquals("DELIVERY", summary.category());
            assertEquals("KING", summary.chessPriority());
            assertEquals(outcomeId.toString(), summary.outcomeId());
            assertNull(summary.rallyCryId()); // not locked, no snapshot
        }

        @Test
        void handlesNullCategoryAndPriority() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, null, null, null);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));

            PlanQualityContext result = provider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID);

            CommitQualitySummary summary = result.commits().get(0);
            assertNull(summary.category());
            assertNull(summary.chessPriority());
            assertNull(summary.outcomeId());
        }

        @Test
        void populatesRallyCryIdFromSnapshot() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            plan.lock(LockType.ON_TIME);
            UUID outcomeId = UUID.randomUUID();
            UUID rallyCryId = UUID.randomUUID();

            WeeklyCommitEntity commit = makeCommit(
                    PLAN_ID, CommitCategory.DELIVERY, ChessPriority.QUEEN, outcomeId);
            commit.populateSnapshot(
                    rallyCryId, "Win New Markets",
                    UUID.randomUUID(), "Expand Outreach",
                    outcomeId, "Reach 100 leads"
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));

            PlanQualityContext result = provider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID);

            CommitQualitySummary summary = result.commits().get(0);
            assertEquals(rallyCryId.toString(), summary.rallyCryId());
        }

        @Test
        void returnsEmptyCommitsWhenPlanHasNoCommits() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of());

            PlanQualityContext result = provider.getPlanQualityContext(ORG_ID, PLAN_ID, USER_ID);

            assertTrue(result.planFound());
            assertTrue(result.commits().isEmpty());
        }
    }

    // ── getPreviousWeekQualityContext ─────────────────────────────────────────

    @Nested
    class GetPreviousWeekQualityContext {

        @Test
        void returnsEmptyContextWhenNoPreviousPlan() {
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START.minusWeeks(1))).thenReturn(Optional.empty());

            PlanQualityContext result =
                    provider.getPreviousWeekQualityContext(ORG_ID, USER_ID, WEEK_START);

            assertFalse(result.planFound());
        }

        @Test
        void returnsPreviousWeekCommits() {
            LocalDate prevWeek = WEEK_START.minusWeeks(1);
            UUID prevPlanId = UUID.randomUUID();
            WeeklyPlanEntity prevPlan = makePlan(prevPlanId, prevWeek);
            WeeklyCommitEntity commit = makeCommit(
                    prevPlanId, CommitCategory.OPERATIONS, ChessPriority.ROOK, null);

            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, prevWeek)).thenReturn(Optional.of(prevPlan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, prevPlanId))
                    .thenReturn(List.of(commit));

            PlanQualityContext result =
                    provider.getPreviousWeekQualityContext(ORG_ID, USER_ID, WEEK_START);

            assertTrue(result.planFound());
            assertEquals(prevWeek.toString(), result.weekStart());
            assertEquals(1, result.commits().size());
            assertEquals("OPERATIONS", result.commits().get(0).category());
        }
    }

    // ── getTeamStrategicAlignmentRate ─────────────────────────────────────────

    @Nested
    class GetTeamStrategicAlignmentRate {

        @Test
        void returnsZeroWhenNoPlansExist() {
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), eq(WEEK_START), eq(WEEK_START))).thenReturn(List.of());

            double rate = provider.getTeamStrategicAlignmentRate(ORG_ID, WEEK_START);

            assertEquals(0.0, rate);
        }

        @Test
        void returnsZeroWhenNoCommitsExist() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), eq(WEEK_START), eq(WEEK_START))).thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            double rate = provider.getTeamStrategicAlignmentRate(ORG_ID, WEEK_START);

            assertEquals(0.0, rate);
        }

        @Test
        void computesRateCorrectly() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            UUID outcomeId = UUID.randomUUID();
            // 2 strategic, 2 non-strategic → 50%
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(PLAN_ID, CommitCategory.DELIVERY, ChessPriority.KING, outcomeId),
                    makeCommit(PLAN_ID, CommitCategory.DELIVERY, ChessPriority.QUEEN, outcomeId),
                    makeCommit(PLAN_ID, CommitCategory.OPERATIONS, ChessPriority.ROOK, null),
                    makeCommit(PLAN_ID, CommitCategory.PEOPLE, ChessPriority.PAWN, null)
            );

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), eq(WEEK_START), eq(WEEK_START))).thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);

            double rate = provider.getTeamStrategicAlignmentRate(ORG_ID, WEEK_START);

            assertEquals(0.5, rate, 0.001);
        }

        @Test
        void returnsOneWhenAllCommitsAreStrategic() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, WEEK_START);
            UUID outcomeId = UUID.randomUUID();
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(PLAN_ID, CommitCategory.DELIVERY, ChessPriority.KING, outcomeId),
                    makeCommit(PLAN_ID, CommitCategory.GTM, ChessPriority.QUEEN, outcomeId)
            );

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), eq(WEEK_START), eq(WEEK_START))).thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);

            double rate = provider.getTeamStrategicAlignmentRate(ORG_ID, WEEK_START);

            assertEquals(1.0, rate, 0.001);
        }
    }
}
