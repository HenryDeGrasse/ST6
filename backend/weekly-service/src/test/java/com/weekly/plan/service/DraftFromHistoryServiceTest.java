package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link DraftFromHistoryService}: carried-forward detection,
 * recurring pattern detection, Levenshtein similarity, and draft plan creation.
 */
class DraftFromHistoryServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private DraftFromHistoryService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        service = new DraftFromHistoryService(planRepository, commitRepository, actualRepository);
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private WeeklyPlanEntity makePlan(UUID planId, LocalDate weekStart) {
        return new WeeklyPlanEntity(planId, ORG_ID, USER_ID, weekStart);
    }

    private WeeklyPlanEntity makeReconciledPlan(UUID planId, LocalDate weekStart) {
        WeeklyPlanEntity plan = makePlan(planId, weekStart);
        plan.lock(LockType.ON_TIME);
        plan.startReconciliation();
        plan.submitReconciliation();
        return plan;
    }

    private WeeklyCommitEntity makeCommit(UUID planId, String title) {
        return new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, title);
    }

    private WeeklyCommitEntity makeCommitWithOutcome(UUID planId, String title, UUID outcomeId) {
        WeeklyCommitEntity commit = makeCommit(planId, title);
        commit.setOutcomeId(outcomeId);
        return commit;
    }

    private WeeklyCommitActualEntity makeActual(UUID commitId, CompletionStatus status) {
        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commitId, ORG_ID);
        actual.setCompletionStatus(status);
        return actual;
    }

    // ── Levenshtein / similarity utilities ──────────────────────────────────

    @Nested
    class LevenshteinDistance {

        @Test
        void identicalStringsReturnZero() {
            assertEquals(0, DraftFromHistoryService.levenshteinDistance("hello", "hello"));
        }

        @Test
        void emptyFirstStringReturnsBLength() {
            assertEquals(5, DraftFromHistoryService.levenshteinDistance("", "hello"));
        }

        @Test
        void emptySecondStringReturnsALength() {
            assertEquals(5, DraftFromHistoryService.levenshteinDistance("hello", ""));
        }

        @Test
        void singleEditDistance() {
            assertEquals(1, DraftFromHistoryService.levenshteinDistance("kitten", "sitten"));
        }

        @Test
        void classicKittenSittingDistance() {
            assertEquals(3, DraftFromHistoryService.levenshteinDistance("kitten", "sitting"));
        }
    }

    @Nested
    class NormalizedLevenshteinDistance {

        @Test
        void identicalTitlesReturnZero() {
            assertEquals(0.0,
                    DraftFromHistoryService.normalizedLevenshteinDistance("Deploy auth", "Deploy auth"));
        }

        @Test
        void nullInputsTreatedAsEmpty() {
            assertEquals(0.0,
                    DraftFromHistoryService.normalizedLevenshteinDistance(null, null));
        }

        @Test
        void slightlyDifferentTitlesBelowThreshold() {
            // "Deploy auth service" vs "Deploy auth services" → 1 edit / 19 chars ≈ 0.05
            double dist = DraftFromHistoryService.normalizedLevenshteinDistance(
                    "Deploy auth service", "Deploy auth services");
            assertTrue(dist < DraftFromHistoryService.SIMILARITY_THRESHOLD);
        }

        @Test
        void completelyDifferentTitlesAboveThreshold() {
            double dist = DraftFromHistoryService.normalizedLevenshteinDistance(
                    "Deploy auth service", "Team meeting sync");
            assertTrue(dist >= DraftFromHistoryService.SIMILARITY_THRESHOLD);
        }

        @Test
        void caseAndWhitespaceSensitivity() {
            // Normalized → lowercase + trim, so these should be treated as equal
            assertEquals(0.0,
                    DraftFromHistoryService.normalizedLevenshteinDistance(
                            "  Deploy Auth  ", "deploy auth"));
        }
    }

    @Nested
    class AreSimilar {

        @Test
        void sameOutcomeIdReturnsTrueRegardlessOfTitle() {
            UUID outcomeId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            WeeklyCommitEntity a = makeCommitWithOutcome(planId, "Task A", outcomeId);
            WeeklyCommitEntity b = makeCommitWithOutcome(planId, "Task B — different name", outcomeId);

            assertTrue(DraftFromHistoryService.areSimilar(a, b));
        }

        @Test
        void differentOutcomeIdsButSimilarTitlesReturnTrue() {
            UUID planId = UUID.randomUUID();
            WeeklyCommitEntity a = makeCommitWithOutcome(planId, "Deploy auth service", UUID.randomUUID());
            WeeklyCommitEntity b = makeCommitWithOutcome(planId, "Deploy auth services", UUID.randomUUID());

            assertTrue(DraftFromHistoryService.areSimilar(a, b));
        }

        @Test
        void nullOutcomeIdAndDissimilarTitleReturnFalse() {
            UUID planId = UUID.randomUUID();
            WeeklyCommitEntity a = makeCommit(planId, "Deploy auth service");
            WeeklyCommitEntity b = makeCommit(planId, "Team all-hands meeting planning");

            assertFalse(DraftFromHistoryService.areSimilar(a, b));
        }
    }

    // ── Draft from history ───────────────────────────────────────────────────

    @Nested
    class DraftFromHistory {

        @Test
        void noHistoricalPlansCreatesEmptyDraft() {
            LocalDate weekStart = currentMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.save(any())).thenReturn(newPlan);

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertNotNull(result.planId());
            assertTrue(result.suggestedCommits().isEmpty());
        }

        @Test
        void rejectsNonMondayWeekStart() {
            LocalDate tuesday = currentMonday().plusDays(1);

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> service.draftFromHistory(ORG_ID, USER_ID, tuesday)
            );
            assertEquals(ErrorCode.INVALID_WEEK_START, ex.getErrorCode());
        }

        @Test
        void rejectsNonDraftTargetPlan() {
            LocalDate weekStart = currentMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity lockedPlan = makePlan(planId, weekStart);
            lockedPlan.lock(LockType.ON_TIME);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.of(lockedPlan));

            assertThrows(PlanStateException.class,
                    () -> service.draftFromHistory(ORG_ID, USER_ID, weekStart));
        }

        @Test
        void carriedForwardFromMostRecentReconciledPlan() {
            LocalDate weekStart = currentMonday();
            LocalDate prevWeekStart = weekStart.minusWeeks(1);

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan = makeReconciledPlan(planId, prevWeekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(reconciledPlan));

            WeeklyCommitEntity notDoneCommit = makeCommit(planId, "Implement payment gateway");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(notDoneCommit));

            WeeklyCommitActualEntity actual = makeActual(notDoneCommit.getId(),
                    CompletionStatus.NOT_DONE);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertEquals(1, result.suggestedCommits().size());
            SuggestedCommit suggestion = result.suggestedCommits().get(0);
            assertEquals("Implement payment gateway", suggestion.title());
            assertEquals(CommitSource.CARRIED_FORWARD, suggestion.source());
        }

        @Test
        void doneCommitsAreNotCarriedForward() {
            LocalDate weekStart = currentMonday();
            LocalDate prevWeekStart = weekStart.minusWeeks(1);

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan = makeReconciledPlan(planId, prevWeekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(reconciledPlan));

            WeeklyCommitEntity doneCommit = makeCommit(planId, "Deploy to production");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(doneCommit));

            WeeklyCommitActualEntity actual = makeActual(doneCommit.getId(), CompletionStatus.DONE);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertTrue(result.suggestedCommits().isEmpty());
        }

        @Test
        void committedWithPartialStatusIsCarriedForward() {
            LocalDate weekStart = currentMonday();
            LocalDate prevWeekStart = weekStart.minusWeeks(1);

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan = makeReconciledPlan(planId, prevWeekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(reconciledPlan));

            WeeklyCommitEntity partialCommit = makeCommit(planId, "Migrate legacy data");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(partialCommit));

            WeeklyCommitActualEntity actual = makeActual(partialCommit.getId(),
                    CompletionStatus.PARTIALLY);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertEquals(1, result.suggestedCommits().size());
            assertEquals(CommitSource.CARRIED_FORWARD, result.suggestedCommits().get(0).source());
        }

        @Test
        void recurringPatternDetectedAcrossTwoConsecutiveWeeks() {
            LocalDate weekStart = currentMonday();
            LocalDate w3 = weekStart.minusWeeks(1);
            LocalDate w2 = weekStart.minusWeeks(2);

            UUID planId3 = UUID.randomUUID();
            UUID planId2 = UUID.randomUUID();
            WeeklyPlanEntity plan3 = makePlan(planId3, w3);
            WeeklyPlanEntity plan2 = makePlan(planId2, w2);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan2, plan3)); // sorted ascending

            // Same title in both weeks → recurring
            WeeklyCommitEntity commit3 = makeCommit(planId3, "Weekly status report");
            WeeklyCommitEntity commit2 = makeCommit(planId2, "Weekly status report");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit2, commit3));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of()); // no actuals (plan not reconciled)

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertEquals(1, result.suggestedCommits().size());
            assertEquals(CommitSource.RECURRING, result.suggestedCommits().get(0).source());
            assertEquals("Weekly status report", result.suggestedCommits().get(0).title());
        }

        @Test
        void recurringPatternDetectedBySameOutcomeId() {
            LocalDate weekStart = currentMonday();
            LocalDate w3 = weekStart.minusWeeks(1);
            LocalDate w2 = weekStart.minusWeeks(2);

            UUID planId3 = UUID.randomUUID();
            UUID planId2 = UUID.randomUUID();
            WeeklyPlanEntity plan3 = makePlan(planId3, w3);
            WeeklyPlanEntity plan2 = makePlan(planId2, w2);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan2, plan3));

            UUID sharedOutcomeId = UUID.randomUUID();
            WeeklyCommitEntity commit3 = makeCommitWithOutcome(planId3, "Task A", sharedOutcomeId);
            WeeklyCommitEntity commit2 = makeCommitWithOutcome(planId2, "Task B", sharedOutcomeId);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit2, commit3));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertEquals(1, result.suggestedCommits().size());
            assertEquals(CommitSource.RECURRING, result.suggestedCommits().get(0).source());
        }

        @Test
        void nonConsecutiveWeekDoesNotTriggerRecurring() {
            LocalDate weekStart = currentMonday();
            LocalDate w3 = weekStart.minusWeeks(1);
            LocalDate w1 = weekStart.minusWeeks(3); // skip week 2

            UUID planId3 = UUID.randomUUID();
            UUID planId1 = UUID.randomUUID();
            WeeklyPlanEntity plan3 = makePlan(planId3, w3);
            WeeklyPlanEntity plan1 = makePlan(planId1, w1);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan1, plan3)); // w1 and w3, not consecutive

            WeeklyCommitEntity commit3 = makeCommit(planId3, "Weekly status report");
            WeeklyCommitEntity commit1 = makeCommit(planId1, "Weekly status report");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit1, commit3));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            // w1 and w3 are not consecutive, so no RECURRING suggestion
            assertTrue(result.suggestedCommits().isEmpty());
        }

        @Test
        void carriedForwardTakesPriorityOverRecurring() {
            LocalDate weekStart = currentMonday();
            LocalDate w3 = weekStart.minusWeeks(1);
            LocalDate w2 = weekStart.minusWeeks(2);

            UUID planId3 = UUID.randomUUID();
            UUID planId2 = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan3 = makeReconciledPlan(planId3, w3);
            WeeklyPlanEntity plan2 = makePlan(planId2, w2);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan2, reconciledPlan3));

            // Same commit in both weeks (recurring) but plan3 is reconciled with NOT_DONE
            WeeklyCommitEntity commit3 = makeCommit(planId3, "Implement login flow");
            WeeklyCommitEntity commit2 = makeCommit(planId2, "Implement login flow");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit2, commit3));

            WeeklyCommitActualEntity actual = makeActual(commit3.getId(), CompletionStatus.NOT_DONE);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            // commit3 is CARRIED_FORWARD; commit2 has same title and would be RECURRING but
            // is deduplicated against the already-suggested CARRIED_FORWARD title
            assertEquals(1, result.suggestedCommits().size());
            assertEquals(CommitSource.CARRIED_FORWARD, result.suggestedCommits().get(0).source());
        }

        @Test
        void existingDraftPlanIsReused() {
            LocalDate weekStart = currentMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            UUID existingPlanId = UUID.randomUUID();
            WeeklyPlanEntity existingDraft = makePlan(existingPlanId, weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.of(existingDraft));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertEquals(existingPlanId, result.planId());
            // planRepository.save should NOT be called since plan already exists
            verify(planRepository, org.mockito.Mockito.never()).save(any(WeeklyPlanEntity.class));
        }

        @Test
        void suggestedCommitIsSavedWithSourceTag() {
            LocalDate weekStart = currentMonday();
            LocalDate prevWeekStart = weekStart.minusWeeks(1);

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan = makeReconciledPlan(planId, prevWeekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(reconciledPlan));

            WeeklyCommitEntity notDoneCommit = makeCommit(planId, "Refactor auth module");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(notDoneCommit));

            WeeklyCommitActualEntity actual = makeActual(notDoneCommit.getId(),
                    CompletionStatus.NOT_DONE);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            ArgumentCaptor<WeeklyCommitEntity> commitCaptor =
                    ArgumentCaptor.forClass(WeeklyCommitEntity.class);
            when(commitRepository.save(commitCaptor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            WeeklyCommitEntity savedCommit = commitCaptor.getValue();
            String[] tags = savedCommit.getTags();
            assertEquals(1, tags.length);
            assertEquals("draft_source:CARRIED_FORWARD", tags[0]);
        }

        @Test
        void returnedSuggestedCommitUsesCreatedDraftCommitId() {
            LocalDate weekStart = currentMonday();
            LocalDate prevWeekStart = weekStart.minusWeeks(1);

            UUID historicalPlanId = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan = makeReconciledPlan(historicalPlanId, prevWeekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(reconciledPlan));

            WeeklyCommitEntity historicalCommit = makeCommit(historicalPlanId, "Finish auth hardening");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(historicalCommit));

            WeeklyCommitActualEntity actual = makeActual(historicalCommit.getId(),
                    CompletionStatus.NOT_DONE);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanEntity newPlan = makePlan(UUID.randomUUID(), weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenReturn(newPlan);
            ArgumentCaptor<WeeklyCommitEntity> commitCaptor =
                    ArgumentCaptor.forClass(WeeklyCommitEntity.class);
            when(commitRepository.save(commitCaptor.capture()))
                    .thenAnswer(inv -> inv.getArgument(0));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            WeeklyCommitEntity savedCommit = commitCaptor.getValue();
            assertEquals(savedCommit.getId(), result.suggestedCommits().get(0).commitId());
            assertFalse(historicalCommit.getId().equals(result.suggestedCommits().get(0).commitId()));
        }

        @Test
        void existingDraftCommitPreventsDuplicateSuggestionOnRetry() {
            LocalDate weekStart = currentMonday();
            LocalDate prevWeekStart = weekStart.minusWeeks(1);

            UUID historicalPlanId = UUID.randomUUID();
            WeeklyPlanEntity reconciledPlan = makeReconciledPlan(historicalPlanId, prevWeekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(reconciledPlan));

            WeeklyCommitEntity historicalCommit = makeCommit(historicalPlanId, "Weekly planning review");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(historicalCommit));

            WeeklyCommitActualEntity actual = makeActual(historicalCommit.getId(),
                    CompletionStatus.NOT_DONE);
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            UUID existingDraftId = UUID.randomUUID();
            WeeklyPlanEntity existingDraft = makePlan(existingDraftId, weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.of(existingDraft));

            WeeklyCommitEntity existingDraftCommit = makeCommit(existingDraftId, "  Weekly   planning review ");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, existingDraftId))
                    .thenReturn(List.of(existingDraftCommit));

            DraftFromHistoryService.DraftFromHistoryResult result =
                    service.draftFromHistory(ORG_ID, USER_ID, weekStart);

            assertTrue(result.suggestedCommits().isEmpty());
            verify(commitRepository, org.mockito.Mockito.never()).save(any(WeeklyCommitEntity.class));
        }
    }
}
