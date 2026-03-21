package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.NextWorkDataProvider.CarryForwardItem;
import com.weekly.shared.NextWorkDataProvider.RcdoCoverageGap;
import com.weekly.shared.NextWorkDataProvider.RecentCommitContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlanNextWorkDataProvider}.
 */
class PlanNextWorkDataProviderTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START =
            LocalDate.now().with(DayOfWeek.MONDAY);

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private PlanNextWorkDataProvider provider;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        provider = new PlanNextWorkDataProvider(planRepository, commitRepository, actualRepository);
    }

    @Test
    void carryForwardItemsUseRootSourceCommitIdForStableFeedbackCorrelation() {
        UUID priorPlanId = UUID.randomUUID();
        UUID latestPlanId = UUID.randomUUID();
        UUID rootCommitId = UUID.randomUUID();
        UUID carriedCommitId = UUID.randomUUID();

        WeeklyPlanEntity priorPlan = plan(priorPlanId, WEEK_START.minusWeeks(2), PlanState.RECONCILED);
        WeeklyPlanEntity latestPlan = plan(latestPlanId, WEEK_START.minusWeeks(1), PlanState.CARRY_FORWARD);

        WeeklyCommitEntity rootCommit = commit(rootCommitId, priorPlanId, "Ship auth", null, null);
        WeeklyCommitEntity carriedCommit = commit(
                carriedCommitId, latestPlanId, "Ship auth", null, rootCommitId);

        when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                ORG_ID, USER_ID, WEEK_START.minusWeeks(2), WEEK_START.minusWeeks(1)))
                .thenReturn(List.of(priorPlan, latestPlan));
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(priorPlanId, latestPlanId)))
                .thenReturn(List.of(rootCommit, carriedCommit));
        when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(rootCommitId, carriedCommitId)))
                .thenReturn(List.of());

        List<CarryForwardItem> result = provider.getRecentCarryForwardItems(
                ORG_ID, USER_ID, WEEK_START, 2);

        assertEquals(1, result.size());
        CarryForwardItem item = result.get(0);
        assertEquals(rootCommitId, item.sourceCommitId());
        assertEquals(2, item.carryForwardWeeks());
        assertEquals(WEEK_START.minusWeeks(2), item.sourceWeekStart());
    }

    @Test
    void recentCommitHistoryIncludesStatusesAndStrategicMetadata() {
        UUID reconciledPlanId = UUID.randomUUID();
        UUID lockedPlanId = UUID.randomUUID();
        UUID doneCommitId = UUID.randomUUID();
        UUID lockedCommitId = UUID.randomUUID();
        UUID outcomeId = UUID.randomUUID();

        WeeklyPlanEntity reconciledPlan = plan(reconciledPlanId, WEEK_START.minusWeeks(2), PlanState.RECONCILED);
        WeeklyPlanEntity lockedPlan = plan(lockedPlanId, WEEK_START.minusWeeks(1), PlanState.LOCKED);

        WeeklyCommitEntity doneCommit = commit(doneCommitId, reconciledPlanId, "Finish auth", outcomeId, null);
        doneCommit.populateSnapshot(null, "Trust", null, "Secure Login", outcomeId, "Auth Outcome");
        WeeklyCommitEntity lockedCommit = commit(lockedCommitId, lockedPlanId, "Prepare rollout", null, null);

        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(doneCommitId, ORG_ID);
        actual.setCompletionStatus(CompletionStatus.DONE);

        when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                ORG_ID, USER_ID, WEEK_START.minusWeeks(4), WEEK_START.minusWeeks(1)))
                .thenReturn(List.of(reconciledPlan, lockedPlan));
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(reconciledPlanId, lockedPlanId)))
                .thenReturn(List.of(doneCommit, lockedCommit));
        when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(doneCommitId, lockedCommitId)))
                .thenReturn(List.of(actual));

        List<RecentCommitContext> result = provider.getRecentCommitHistory(ORG_ID, USER_ID, WEEK_START, 4);

        assertEquals(2, result.size());
        assertEquals(lockedCommitId, result.get(0).commitId(), "Newest week should come first");
        assertEquals("LOCKED", result.get(0).completionStatus());
        assertEquals(doneCommitId, result.get(1).commitId());
        assertEquals("DONE", result.get(1).completionStatus());
        assertEquals("Auth Outcome", result.get(1).outcomeName());
        assertEquals("Secure Login", result.get(1).objectiveName());
        assertEquals("Trust", result.get(1).rallyCryName());
    }

    @Test
    void recentCommitHistoryTreatsMissingActualsInReconciledPlansAsNotDone() {
        UUID planId = UUID.randomUUID();
        UUID commitId = UUID.randomUUID();

        WeeklyPlanEntity reconciledPlan = plan(planId, WEEK_START.minusWeeks(1), PlanState.RECONCILED);
        WeeklyCommitEntity commit = commit(commitId, planId, "Carry item", null, null);

        when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                ORG_ID, USER_ID, WEEK_START.minusWeeks(4), WEEK_START.minusWeeks(1)))
                .thenReturn(List.of(reconciledPlan));
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(planId)))
                .thenReturn(List.of(commit));
        when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(commitId)))
                .thenReturn(List.of());

        List<RecentCommitContext> result = provider.getRecentCommitHistory(ORG_ID, USER_ID, WEEK_START, 4);

        assertEquals(1, result.size());
        assertEquals("NOT_DONE", result.get(0).completionStatus());
    }

    @Test
    void coverageGapsAreRankedByConsecutiveMissingWeeksAndRequireAtLeastTwoWeeks() {
        UUID planFourWeeksGapId = UUID.randomUUID();
        UUID planTwoWeeksGapId = UUID.randomUUID();
        UUID planOneWeekGapId = UUID.randomUUID();
        UUID outcomeFourWeeks = UUID.randomUUID();
        UUID outcomeTwoWeeks = UUID.randomUUID();
        UUID outcomeOneWeek = UUID.randomUUID();

        WeeklyPlanEntity fourWeeksGapPlan = plan(
                planFourWeeksGapId, WEEK_START.minusWeeks(5), PlanState.RECONCILED);
        WeeklyPlanEntity twoWeeksGapPlan = plan(
                planTwoWeeksGapId, WEEK_START.minusWeeks(3), PlanState.RECONCILED);
        WeeklyPlanEntity oneWeekGapPlan = plan(
                planOneWeekGapId, WEEK_START.minusWeeks(2), PlanState.RECONCILED);

        WeeklyCommitEntity fourWeeksGapCommit = commit(
                UUID.randomUUID(), planFourWeeksGapId, "Outcome A", outcomeFourWeeks, null);
        fourWeeksGapCommit.populateSnapshot(null, "RC A", null, "OBJ A", outcomeFourWeeks, "Outcome A");

        WeeklyCommitEntity twoWeeksGapCommit = commit(
                UUID.randomUUID(), planTwoWeeksGapId, "Outcome B", outcomeTwoWeeks, null);
        twoWeeksGapCommit.populateSnapshot(null, "RC B", null, "OBJ B", outcomeTwoWeeks, "Outcome B");

        WeeklyCommitEntity oneWeekGapCommit = commit(
                UUID.randomUUID(), planOneWeekGapId, "Outcome C", outcomeOneWeek, null);
        oneWeekGapCommit.populateSnapshot(null, "RC C", null, "OBJ C", outcomeOneWeek, "Outcome C");

        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                ORG_ID, WEEK_START.minusWeeks(8), WEEK_START.minusWeeks(1)))
                .thenReturn(List.of(fourWeeksGapPlan, twoWeeksGapPlan, oneWeekGapPlan));
        when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(
                ORG_ID,
                List.of(planFourWeeksGapId, planTwoWeeksGapId, planOneWeekGapId)))
                .thenReturn(List.of(fourWeeksGapCommit, twoWeeksGapCommit, oneWeekGapCommit));

        List<RcdoCoverageGap> result = provider.getTeamCoverageGaps(ORG_ID, WEEK_START, 4, 8);

        assertEquals(2, result.size());
        assertEquals(outcomeFourWeeks.toString(), result.get(0).outcomeId());
        assertEquals(4, result.get(0).weeksMissing());
        assertEquals(outcomeTwoWeeks.toString(), result.get(1).outcomeId());
        assertEquals(2, result.get(1).weeksMissing());
        assertTrue(result.stream().noneMatch(g -> outcomeOneWeek.toString().equals(g.outcomeId())));
    }

    private WeeklyPlanEntity plan(UUID planId, LocalDate weekStart, PlanState state) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, weekStart);
        plan.setState(state);
        return plan;
    }

    private WeeklyCommitEntity commit(
            UUID commitId,
            UUID planId,
            String title,
            UUID outcomeId,
            UUID carriedFromCommitId
    ) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, planId, title);
        commit.setOutcomeId(outcomeId);
        commit.setCarriedFromCommitId(carriedFromCommitId);
        return commit;
    }
}
