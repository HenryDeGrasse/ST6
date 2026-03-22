package com.weekly.quickupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressNoteSource;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitNotFoundException;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanNotFoundException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link QuickUpdateService}: batch check-in validations and note provenance capture.
 */
class QuickUpdateServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private ProgressEntryRepository progressEntryRepository;
    private com.weekly.compatibility.dualwrite.DualWriteService dualWriteService;
    private QuickUpdateService quickUpdateService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        dualWriteService = mock(com.weekly.compatibility.dualwrite.DualWriteService.class);
        quickUpdateService = new QuickUpdateService(
                planRepository, commitRepository, progressEntryRepository, dualWriteService);
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
                    new QuickUpdateItemDto(commitId1, "ON_TRACK", "Going well", null, null, null),
                    new QuickUpdateItemDto(commitId2, "AT_RISK", "Some concerns", null, null, null)
            );

            QuickUpdateResponseDto response =
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates);

            verify(progressEntryRepository, times(2)).save(any());
            verify(dualWriteService, times(2)).onQuickUpdateNote(any(), any(), eq(USER_ID));
            assertEquals(2, response.updatedCount());
            assertNotNull(response.entries());
            assertEquals(2, response.entries().size());
        }

        @Test
        void storesProvidedNoteProvenanceMetadata() {
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);
            WeeklyCommitEntity commit = makeCommit(commitId, PLAN_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(
                            commitId,
                            "ON_TRACK",
                            "Wrapped API integration",
                            "SUGGESTION_ACCEPTED",
                            "Wrapped API integration",
                            "ai"
                    )
            );

            quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates);

            ArgumentCaptor<ProgressEntryEntity> captor =
                    ArgumentCaptor.forClass(ProgressEntryEntity.class);
            verify(progressEntryRepository).save(captor.capture());

            ProgressEntryEntity saved = captor.getValue();
            assertEquals(ProgressNoteSource.SUGGESTION_ACCEPTED, saved.getNoteSource());
            assertEquals("Wrapped API integration", saved.getSelectedSuggestionText());
            assertEquals("ai", saved.getSelectedSuggestionSource());
        }

        @Test
        void defaultsNoteSourceToUnknownWhenOmitted() {
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);
            WeeklyCommitEntity commit = makeCommit(commitId, PLAN_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            quickUpdateService.batchCheckIn(
                    ORG_ID,
                    USER_ID,
                    PLAN_ID,
                    List.of(new QuickUpdateItemDto(commitId, "ON_TRACK", "Typed note", null, null, null))
            );

            ArgumentCaptor<ProgressEntryEntity> captor =
                    ArgumentCaptor.forClass(ProgressEntryEntity.class);
            verify(progressEntryRepository).save(captor.capture());
            assertEquals(ProgressNoteSource.UNKNOWN, captor.getValue().getNoteSource());
        }

        @Test
        void rejectsInvalidNoteSource() {
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(PLAN_ID, USER_ID, PlanState.LOCKED);
            WeeklyCommitEntity commit = makeCommit(commitId, PLAN_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));

            assertThrows(
                    PlanValidationException.class,
                    () -> quickUpdateService.batchCheckIn(
                            ORG_ID,
                            USER_ID,
                            PLAN_ID,
                            List.of(new QuickUpdateItemDto(
                                    commitId,
                                    "ON_TRACK",
                                    "Typed note",
                                    "IMPORTED_FROM_MARS",
                                    null,
                                    null
                            ))
                    )
            );

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsPlanNotFoundWhenPlanMissing() {
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.empty());

            List<QuickUpdateItemDto> updates = List.of(
                    new QuickUpdateItemDto(UUID.randomUUID(), "ON_TRACK", null, null, null, null)
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
                    new QuickUpdateItemDto(UUID.randomUUID(), "ON_TRACK", null, null, null, null)
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
                    new QuickUpdateItemDto(UUID.randomUUID(), "ON_TRACK", null, null, null, null)
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
                    new QuickUpdateItemDto(unknownCommitId, "ON_TRACK", null, null, null, null)
            );

            assertThrows(CommitNotFoundException.class, () ->
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates));

            verify(progressEntryRepository, never()).save(any());
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
                    new QuickUpdateItemDto(commitId, "ON_TRACK", null, null, null, null)
            );

            QuickUpdateResponseDto response =
                    quickUpdateService.batchCheckIn(ORG_ID, USER_ID, PLAN_ID, updates);

            verify(progressEntryRepository, times(1)).save(any());
            assertEquals(1, response.updatedCount());
        }
    }
}
