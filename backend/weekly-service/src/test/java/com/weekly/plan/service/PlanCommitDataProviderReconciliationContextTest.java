package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CommitDataProvider;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the enriched reconciliation context in {@link PlanCommitDataProvider}.
 *
 * <p>Tests cover the three new context signals added for step-15:
 * <ul>
 *   <li>Structured check-in history from {@code progress_entries}</li>
 *   <li>Carry-forward chain statuses from {@code carried_from_commit_id}</li>
 *   <li>Team category completion rates from historical plan actuals</li>
 * </ul>
 */
class PlanCommitDataProviderReconciliationContextTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START =
            LocalDate.now().with(DayOfWeek.MONDAY);

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private ProgressEntryRepository progressEntryRepository;
    private WeeklyCommitActualRepository actualRepository;
    private PlanCommitDataProvider provider;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        provider = new PlanCommitDataProvider(
                planRepository, commitRepository, progressEntryRepository, actualRepository);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private WeeklyPlanEntity makePlan(UUID planId, LocalDate weekStart) {
        return new WeeklyPlanEntity(planId, ORG_ID, USER_ID, weekStart);
    }

    private WeeklyCommitEntity makeCommit(UUID planId, String title) {
        return new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, title);
    }

    private WeeklyCommitEntity makeCommitWithCategory(UUID planId, String title,
            CommitCategory category) {
        WeeklyCommitEntity c = makeCommit(planId, title);
        c.setCategory(category);
        return c;
    }

    private WeeklyCommitActualEntity makeActual(UUID commitId, CompletionStatus status) {
        WeeklyCommitActualEntity a = new WeeklyCommitActualEntity(commitId, ORG_ID);
        a.setCompletionStatus(status);
        return a;
    }

    private WeeklyCommitEntity makeCommitWithId(UUID commitId, UUID planId, String title) {
        return new WeeklyCommitEntity(commitId, ORG_ID, planId, title);
    }

    private ProgressEntryEntity makeProgressEntry(UUID commitId, ProgressStatus status, String note) {
        return new ProgressEntryEntity(UUID.randomUUID(), ORG_ID, commitId, status, note);
    }

    private void stubPlan(UUID planId) {
        when(planRepository.findByOrgIdAndId(ORG_ID, planId))
                .thenReturn(Optional.of(makePlan(planId, WEEK_START)));
    }

    // ── planExists (backward-compat) ──────────────────────────────────────────

    @Nested
    class PlanExists {

        @Test
        void returnsTrueWhenPlanFound() {
            stubPlan(PLAN_ID);
            assertTrue(provider.planExists(ORG_ID, PLAN_ID));
        }

        @Test
        void returnsFalseWhenPlanNotFound() {
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.empty());
            assertTrue(!provider.planExists(ORG_ID, PLAN_ID));
        }
    }

    // ── Check-in history ──────────────────────────────────────────────────────

    @Nested
    class CheckInHistory {

        @Test
        void emptyCheckInHistoryWhenNoneExist() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Build feature");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertEquals(1, summaries.size());
            assertTrue(summaries.get(0).checkInHistory().isEmpty(),
                    "Check-in history should be empty when no progress entries exist");
        }

        @Test
        void populatesCheckInHistoryFromProgressEntries() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Build feature");
            UUID commitId = commit.getId();

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            makeProgressEntry(commitId, ProgressStatus.ON_TRACK, "good progress"),
                            makeProgressEntry(commitId, ProgressStatus.AT_RISK, "blocked by API")
                    ));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertEquals(1, summaries.size());
            List<CommitDataProvider.CheckInEntry> history = summaries.get(0).checkInHistory();
            assertEquals(2, history.size());
            assertEquals("ON_TRACK", history.get(0).status());
            assertEquals("good progress", history.get(0).note());
            assertEquals("AT_RISK", history.get(1).status());
            assertEquals("blocked by API", history.get(1).note());
        }

        @Test
        void checkInHistoryIsBatchLoadedForAllCommits() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity commitA = makeCommit(PLAN_ID, "Feature A");
            WeeklyCommitEntity commitB = makeCommit(PLAN_ID, "Feature B");

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commitA, commitB));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            makeProgressEntry(commitA.getId(), ProgressStatus.ON_TRACK, "on track"),
                            makeProgressEntry(commitB.getId(), ProgressStatus.BLOCKED, "needs access")
                    ));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertEquals(2, summaries.size());
            CommitDataProvider.CommitSummary summaryA = summaries.stream()
                    .filter(s -> s.title().equals("Feature A")).findFirst().orElseThrow();
            CommitDataProvider.CommitSummary summaryB = summaries.stream()
                    .filter(s -> s.title().equals("Feature B")).findFirst().orElseThrow();

            assertEquals(1, summaryA.checkInHistory().size());
            assertEquals("ON_TRACK", summaryA.checkInHistory().get(0).status());
            assertEquals(1, summaryB.checkInHistory().size());
            assertEquals("BLOCKED", summaryB.checkInHistory().get(0).status());
        }
    }

    // ── Carry-forward statuses ────────────────────────────────────────────────

    @Nested
    class CarryForwardStatuses {

        @Test
        void emptyPriorStatusesWhenNotCarriedForward() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Fresh task");
            // No carriedFromCommitId set

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertTrue(summaries.get(0).priorCompletionStatuses().isEmpty(),
                    "No prior statuses for a commit that was not carried forward");
        }

        @Test
        void populatesPriorStatusFromDirectAncestorActual() {
            stubPlan(PLAN_ID);
            UUID ancestorId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Carry task");
            commit.setCarriedFromCommitId(ancestorId);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());

            // Actual for ancestor: PARTIALLY done
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(makeActual(ancestorId, CompletionStatus.PARTIALLY)));

            // Ancestor commit has no further ancestor
            WeeklyCommitEntity ancestorCommit = makeCommit(UUID.randomUUID(), "Ancestor task");
            when(commitRepository.findAllById(any()))
                    .thenReturn(List.of(ancestorCommit)); // no carriedFromCommitId → chain stops

            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            List<String> priorStatuses = summaries.get(0).priorCompletionStatuses();
            assertEquals(1, priorStatuses.size());
            assertEquals("PARTIALLY", priorStatuses.get(0),
                    "Should record PARTIALLY as the prior status from the direct ancestor");
        }

        @Test
        void traversesMultipleLevelsOfCarryForwardChain() {
            stubPlan(PLAN_ID);
            UUID grandAncestorId = UUID.randomUUID();
            UUID ancestorId = UUID.randomUUID();
            UUID histPlanId = UUID.randomUUID();

            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Repeatedly carried task");
            commit.setCarriedFromCommitId(ancestorId);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());

            // Level 1: ancestor commit (id == ancestorId) points further to grandAncestor
            WeeklyCommitEntity ancestorCommit = makeCommitWithId(ancestorId, histPlanId, "Ancestor");
            ancestorCommit.setCarriedFromCommitId(grandAncestorId);

            // Level 2: grandAncestor commit (id == grandAncestorId) has no further ancestor
            WeeklyCommitEntity grandAncestorCommit =
                    makeCommitWithId(grandAncestorId, histPlanId, "GrandAncestor");
            // No carriedFromCommitId on grandAncestor → chain stops here

            // findAllById must return entities whose getId() matches the requested IDs
            when(commitRepository.findAllById(List.of(ancestorId)))
                    .thenReturn(List.of(ancestorCommit));
            when(commitRepository.findAllById(List.of(grandAncestorId)))
                    .thenReturn(List.of(grandAncestorCommit));

            // Actuals: ancestor = NOT_DONE, grandAncestor = PARTIALLY
            when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(ancestorId)))
                    .thenReturn(List.of(makeActual(ancestorId, CompletionStatus.NOT_DONE)));
            when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(grandAncestorId)))
                    .thenReturn(List.of(makeActual(grandAncestorId, CompletionStatus.PARTIALLY)));

            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            List<String> priorStatuses = summaries.get(0).priorCompletionStatuses();
            assertEquals(2, priorStatuses.size(),
                    "Should traverse two levels of the carry-forward chain");
            assertEquals("NOT_DONE", priorStatuses.get(0), "Most-recent ancestor (level 1) first");
            assertEquals("PARTIALLY", priorStatuses.get(1), "Older ancestor (level 2) second");
        }

        @Test
        void recordsUnknownWhenAncestorHasNoActual() {
            stubPlan(PLAN_ID);
            UUID ancestorId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Task without prior actual");
            commit.setCarriedFromCommitId(ancestorId);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());

            // No actual found for ancestor
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(commitRepository.findAllById(any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            List<String> priorStatuses = summaries.get(0).priorCompletionStatuses();
            assertEquals(1, priorStatuses.size());
            assertEquals("UNKNOWN", priorStatuses.get(0),
                    "Should record UNKNOWN when no actual exists for the ancestor");
        }
    }

    // ── Category completion rates ─────────────────────────────────────────────

    @Nested
    class CategoryCompletionRates {

        @Test
        void nullCategoryRateWhenCommitHasNoCategory() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity commit = makeCommit(PLAN_ID, "Uncategorised task");
            // category is null

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertNull(summaries.get(0).categoryCompletionRateContext(),
                    "Category rate context should be null for commits with no category");
        }

        @Test
        void nullCategoryRateWhenNoHistoricalData() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity commit = makeCommitWithCategory(PLAN_ID, "Ops task",
                    CommitCategory.OPERATIONS);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of()); // no historical plans

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertNull(summaries.get(0).categoryCompletionRateContext(),
                    "Category rate should be null when there are no historical plans");
        }

        @Test
        void computesCategoryRateFromHistoricalActuals() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity currentCommit = makeCommitWithCategory(PLAN_ID, "Ops task",
                    CommitCategory.OPERATIONS);
            UUID currentPlanId = PLAN_ID;

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, currentPlanId))
                    .thenReturn(List.of(currentCommit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            // Current commit has no carry-forward
            when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID,
                    List.of(currentCommit.getId())))
                    .thenReturn(List.of());
            when(commitRepository.findAllById(any())).thenReturn(List.of());

            // Historical plans
            UUID histPlanId = UUID.randomUUID();
            WeeklyPlanEntity histPlan = makePlan(histPlanId, WEEK_START.minusWeeks(1));
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(histPlan));

            // Historical commits: 3 OPERATIONS commits (DONE, DONE, NOT_DONE) → 67%
            WeeklyCommitEntity h1 = makeCommitWithCategory(histPlanId, "H1", CommitCategory.OPERATIONS);
            WeeklyCommitEntity h2 = makeCommitWithCategory(histPlanId, "H2", CommitCategory.OPERATIONS);
            WeeklyCommitEntity h3 = makeCommitWithCategory(histPlanId, "H3", CommitCategory.OPERATIONS);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(h1, h2, h3));

            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID),
                    org.mockito.ArgumentMatchers.argThat(ids ->
                            ids.contains(h1.getId()) || ids.contains(h2.getId()))))
                    .thenReturn(List.of(
                            makeActual(h1.getId(), CompletionStatus.DONE),
                            makeActual(h2.getId(), CompletionStatus.DONE),
                            makeActual(h3.getId(), CompletionStatus.NOT_DONE)
                    ));

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, currentPlanId);

            String rateContext = summaries.get(0).categoryCompletionRateContext();
            assertNotNull(rateContext, "Category rate should be computed when sufficient data exists");
            assertTrue(rateContext.contains("OPERATIONS"), "Should mention category name");
            assertTrue(rateContext.contains("67%"), "Should compute 2/3 = 67% DONE rate");
            assertTrue(rateContext.contains("DONE"), "Should mention DONE in the context");
        }

        @Test
        void omitsCategoryRateBelowMinSampleThreshold() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity currentCommit = makeCommitWithCategory(PLAN_ID, "Ops task",
                    CommitCategory.OPERATIONS);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(currentCommit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID,
                    List.of(currentCommit.getId())))
                    .thenReturn(List.of());
            when(commitRepository.findAllById(any())).thenReturn(List.of());

            // Historical plan with only 2 OPERATIONS commits (below MIN_CATEGORY_SAMPLE=3)
            UUID histPlanId = UUID.randomUUID();
            WeeklyPlanEntity histPlan = makePlan(histPlanId, WEEK_START.minusWeeks(1));
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(histPlan));

            WeeklyCommitEntity h1 = makeCommitWithCategory(histPlanId, "H1", CommitCategory.OPERATIONS);
            WeeklyCommitEntity h2 = makeCommitWithCategory(histPlanId, "H2", CommitCategory.OPERATIONS);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(h1, h2));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID),
                    org.mockito.ArgumentMatchers.argThat(ids ->
                            ids.contains(h1.getId()) || ids.contains(h2.getId()))))
                    .thenReturn(List.of(
                            makeActual(h1.getId(), CompletionStatus.DONE),
                            makeActual(h2.getId(), CompletionStatus.DONE)
                    ));

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertNull(summaries.get(0).categoryCompletionRateContext(),
                    "Category rate should be omitted when sample size < " + PlanCommitDataProvider.MIN_CATEGORY_SAMPLE);
        }

        @Test
        void computesRatesForMultipleCategories() {
            stubPlan(PLAN_ID);
            WeeklyCommitEntity opsCommit = makeCommitWithCategory(PLAN_ID, "Ops task",
                    CommitCategory.OPERATIONS);
            WeeklyCommitEntity delCommit = makeCommitWithCategory(PLAN_ID, "Delivery task",
                    CommitCategory.DELIVERY);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(opsCommit, delCommit));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(actualRepository.findByOrgIdAndCommitIdIn(ORG_ID,
                    List.of(opsCommit.getId(), delCommit.getId())))
                    .thenReturn(List.of());
            when(commitRepository.findAllById(any())).thenReturn(List.of());

            // Historical: 3 OPERATIONS (all DONE = 100%) + 3 DELIVERY (1 DONE = 33%)
            UUID histPlanId = UUID.randomUUID();
            WeeklyPlanEntity histPlan = makePlan(histPlanId, WEEK_START.minusWeeks(1));
            when(planRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(histPlan));

            WeeklyCommitEntity o1 = makeCommitWithCategory(histPlanId, "O1", CommitCategory.OPERATIONS);
            WeeklyCommitEntity o2 = makeCommitWithCategory(histPlanId, "O2", CommitCategory.OPERATIONS);
            WeeklyCommitEntity o3 = makeCommitWithCategory(histPlanId, "O3", CommitCategory.OPERATIONS);
            WeeklyCommitEntity d1 = makeCommitWithCategory(histPlanId, "D1", CommitCategory.DELIVERY);
            WeeklyCommitEntity d2 = makeCommitWithCategory(histPlanId, "D2", CommitCategory.DELIVERY);
            WeeklyCommitEntity d3 = makeCommitWithCategory(histPlanId, "D3", CommitCategory.DELIVERY);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(o1, o2, o3, d1, d2, d3));

            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID),
                    org.mockito.ArgumentMatchers.argThat(ids ->
                            ids.contains(o1.getId()) || ids.contains(d1.getId()))))
                    .thenReturn(List.of(
                            makeActual(o1.getId(), CompletionStatus.DONE),
                            makeActual(o2.getId(), CompletionStatus.DONE),
                            makeActual(o3.getId(), CompletionStatus.DONE),
                            makeActual(d1.getId(), CompletionStatus.DONE),
                            makeActual(d2.getId(), CompletionStatus.NOT_DONE),
                            makeActual(d3.getId(), CompletionStatus.NOT_DONE)
                    ));

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            CommitDataProvider.CommitSummary opsSummary = summaries.stream()
                    .filter(s -> s.title().equals("Ops task")).findFirst().orElseThrow();
            CommitDataProvider.CommitSummary delSummary = summaries.stream()
                    .filter(s -> s.title().equals("Delivery task")).findFirst().orElseThrow();

            assertNotNull(opsSummary.categoryCompletionRateContext());
            assertTrue(opsSummary.categoryCompletionRateContext().contains("OPERATIONS"));
            assertTrue(opsSummary.categoryCompletionRateContext().contains("100%"));

            assertNotNull(delSummary.categoryCompletionRateContext());
            assertTrue(delSummary.categoryCompletionRateContext().contains("DELIVERY"));
            assertTrue(delSummary.categoryCompletionRateContext().contains("33%"));
        }
    }

    // ── Empty plan / plan not found ───────────────────────────────────────────

    @Nested
    class EmptyAndMissing {

        @Test
        void returnsEmptyWhenPlanNotFound() {
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.empty());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertTrue(summaries.isEmpty());
        }

        @Test
        void returnsEmptyWhenPlanHasNoCommits() {
            stubPlan(PLAN_ID);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of());

            List<CommitDataProvider.CommitSummary> summaries =
                    provider.getCommitSummaries(ORG_ID, PLAN_ID);

            assertTrue(summaries.isEmpty());
        }
    }
}
