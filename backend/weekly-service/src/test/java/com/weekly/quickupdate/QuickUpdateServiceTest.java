package com.weekly.quickupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitNotFoundException;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanNotFoundException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.usermodel.UserUpdatePatternService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuickUpdateService}: batch check-in validations and pattern recording.
 */
class QuickUpdateServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private ProgressEntryRepository progressEntryRepository;
    private UserUpdatePatternService userUpdatePatternService;
    private QuickUpdateService quickUpdateService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        userUpdatePatternService = mock(UserUpdatePatternService.class);
        quickUpdateService = new QuickUpdateService(
                planRepository, commitRepository, progressEntryRepository, userUpdatePatternService);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WeeklyCommitEntity makeCommit(UUID commitId, UUID planId) {
        return new WeeklyCommitEntity(commitId, ORG_ID, planId, "Test commit");
    }

    private WeeklyPlanEntity makePlan(UUID planId, UUID ownerUserId, PlanState state) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                planId, ORG_ID, ownerUserId, LocalDate.of(2025, 1, 6));
        plan.setState(state);
        return plan;
    }

    // ─── BatchCheckIn ─────────────────────────────────────────────────────────

    @Nested
    class BatchCheckIn {

        @Test
        void successfulBatchCreatesEntriesForEachItem() {
            // Arrange
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);
            WeeklyCommitEntity commit1 = makeCommit(commitId1, PLAN_ID);
            WeeklyCommitEntity commit2 = makeCommit(commitId2, PLAN_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit1, commit2));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(commitId1, "ON_TRACK", "Going well"),
                    new QuickUpdateItemDto(commitId2, "AT_RISK", "Some concerns")
            );

            // Act
            QuickUpdateResponseDto response =
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates);

            // Assert
            verify(progressEntryRepository, times(2)).save(any());
            assertEquals(2, response.updatedCount());
            assertNotNull(response.entries());
            assertEquals(2, response.entries().size());
        }

        @Test
        void throwsPlanNotFoundWhenPlanMissing() {
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.empty());

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(UUID.randomUUID(), "ON_TRACK", null)
            );

            assertThrows(PlanNotFoundException.class, () ->
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates));

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsForbiddenWhenPlanBelongsToDifferentUser() {
            UUID differentUserId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(PLAN_ID, differentUserId, PlanState.LOCKED);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(UUID.randomUUID(), "ON_TRACK", null)
            );

            assertThrows(PlanAccessForbiddenException.class, () ->
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates));

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsPlanStateExceptionWhenDraft() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.DRAFT);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(UUID.randomUUID(), "ON_TRACK", null)
            );

            assertThrows(PlanStateException.class, () ->
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates));

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsCommitNotFoundWhenCommitNotInPlan() {
            UUID unknownCommitId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of());

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(unknownCommitId, "ON_TRACK", null)
            );

            assertThrows(CommitNotFoundException.class, () ->
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates));

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void recordsPatternsForNonEmptyNotes() {
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);
            WeeklyCommitEntity commit1 = makeCommit(commitId1, PLAN_ID);
            commit1.setCategory(CommitCategory.DELIVERY);
            WeeklyCommitEntity commit2 = makeCommit(commitId2, PLAN_ID);
            commit2.setCategory(CommitCategory.OPERATIONS);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit1, commit2));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(commitId1, "ON_TRACK", "Making good progress"),
                    new QuickUpdateItemDto(commitId2, "BLOCKED", "")  // empty note
            );

            quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates);

            // recordPattern called only for the item with a non-empty note
            verify(userUpdatePatternService, times(1))
                    .recordPattern(ORG_ID, USER_ID, "DELIVERY", "Making good progress");
            verify(userUpdatePatternService, never())
                    .recordPattern(any(), any(), any(), org.mockito.ArgumentMatchers.eq(""));
        }

        @Test
        void handlesEmptyUpdatesList() {
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of());

            QuickUpdateResponseDto response =
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, List.of());

            verify(progressEntryRepository, never()).save(any());
            assertEquals(0, response.updatedCount());
        }

        @Test
        void acceptsReconcillingState() {
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.RECONCILING);
            WeeklyCommitEntity commit = makeCommit(commitId, PLAN_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(commitId, "ON_TRACK", null)
            );

            QuickUpdateResponseDto response =
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates);

            verify(progressEntryRepository, times(1)).save(any());
            assertEquals(1, response.updatedCount());
        }
    }
}
