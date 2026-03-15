package com.weekly.plan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.audit.AuditService;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.CreateCommitRequest;
import com.weekly.plan.dto.UpdateCommitRequest;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommitService}: CRUD, state enforcement, and field freezing.
 */
class CommitServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository commitActualRepository;
    private CommitValidator commitValidator;
    private AuditService auditService;
    private OutboxService outboxService;
    private CommitService commitService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        commitActualRepository = mock(WeeklyCommitActualRepository.class);
        commitValidator = new CommitValidator();
        auditService = mock(AuditService.class);
        outboxService = mock(OutboxService.class);
        commitService = new CommitService(planRepository, commitRepository, commitActualRepository,
                commitValidator, auditService, outboxService);
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private Map<String, Object> parseAuditReason(String reason) {
        try {
            return OBJECT_MAPPER.readValue(reason, new TypeReference<>() { });
        } catch (Exception e) {
            throw new AssertionError("Expected JSON audit reason but got: " + reason, e);
        }
    }

    private WeeklyPlanEntity draftPlan() {
        UUID planId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
        when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
        return plan;
    }

    private WeeklyPlanEntity lockedPlan() {
        WeeklyPlanEntity plan = draftPlan();
        plan.lock(LockType.ON_TIME);
        return plan;
    }

    // ── Create Commit ────────────────────────────────────────

    @Nested
    class CreateCommit {

        @Test
        void createsCommitInDraftPlan() {
            WeeklyPlanEntity plan = draftPlan();
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateCommitRequest request = new CreateCommitRequest(
                    "Deliver feature X", "Build the thing",
                    "KING", "DELIVERY",
                    UUID.randomUUID().toString(), null,
                    "Feature shipped", 0.9, new String[]{"feature", "sprint1"}
            );

            WeeklyCommitResponse result = commitService.createCommit(ORG_ID, plan.getId(), request, USER_ID);

            assertEquals("Deliver feature X", result.title());
            assertEquals("Build the thing", result.description());
            assertEquals("KING", result.chessPriority());
            assertEquals("DELIVERY", result.category());
            assertNotNull(result.outcomeId());
            assertEquals("Feature shipped", result.expectedResult());
            assertEquals(List.of("feature", "sprint1"), List.of(result.tags()));
            verify(commitRepository).save(any(WeeklyCommitEntity.class));
        }

        @Test
        void createsMinimalCommit() {
            WeeklyPlanEntity plan = draftPlan();
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateCommitRequest request = new CreateCommitRequest(
                    "Quick task", null, null, null, null, null, null, null, null
            );

            WeeklyCommitResponse result = commitService.createCommit(ORG_ID, plan.getId(), request, USER_ID);

            assertEquals("Quick task", result.title());
            // Validation errors expected: missing chess priority and RCDO/reason
            assertTrue(result.validationErrors().size() >= 2);
        }

        @Test
        void rejectsCreateOnLockedPlan() {
            WeeklyPlanEntity plan = lockedPlan();

            CreateCommitRequest request = new CreateCommitRequest(
                    "New task", null, null, null, null, null, null, null, null
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> commitService.createCommit(ORG_ID, plan.getId(), request, USER_ID)
            );
            assertEquals(ErrorCode.PLAN_NOT_IN_DRAFT, ex.getErrorCode());
        }

        @Test
        void rejectsCreateWhenOutcomeIdIsNotUuid() {
            WeeklyPlanEntity plan = draftPlan();

            CreateCommitRequest request = new CreateCommitRequest(
                    "Task", null, "KING", "DELIVERY",
                    "out-010", null, null, null, null
            );

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> commitService.createCommit(ORG_ID, plan.getId(), request, USER_ID)
            );
            assertEquals(ErrorCode.MISSING_RCDO_OR_REASON, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("UUID"));
        }
    }

    // ── List Commits ─────────────────────────────────────────

    @Nested
    class ListCommits {

        @Test
        void returnsCommitsWithValidationErrors() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                    .thenReturn(List.of(commit));

            List<WeeklyCommitResponse> results = commitService.listCommits(ORG_ID, plan.getId());

            assertEquals(1, results.size());
            // Should have validation errors (missing chess priority + missing RCDO)
            assertTrue(results.get(0).validationErrors().size() >= 2);
        }

        @Test
        void returnsEmptyListForPlanWithNoCommits() {
            WeeklyPlanEntity plan = draftPlan();
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                    .thenReturn(List.of());

            List<WeeklyCommitResponse> results = commitService.listCommits(ORG_ID, plan.getId());

            assertTrue(results.isEmpty());
        }

        @Test
        void includesActualsForCarryForwardPlans() {
            WeeklyPlanEntity plan = draftPlan();
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();
            plan.carryForward();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setActualResult("Delivered the work");

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                    .thenReturn(List.of(commit));
            when(commitActualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(commit.getId())))
                    .thenReturn(List.of(actual));

            List<WeeklyCommitResponse> results = commitService.listCommits(ORG_ID, plan.getId());

            assertEquals(1, results.size());
            assertNotNull(results.get(0).actual());
            assertEquals("Delivered the work", results.get(0).actual().actualResult());
        }
    }

    @Nested
    class GetCommit {

        @Test
        void includesActualsForCarryForwardPlans() {
            WeeklyPlanEntity plan = draftPlan();
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();
            plan.carryForward();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setActualResult("Delivered the work");

            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitActualRepository.findByOrgIdAndCommitId(ORG_ID, commit.getId()))
                    .thenReturn(Optional.of(actual));

            WeeklyCommitResponse result = commitService.getCommit(ORG_ID, commit.getId());

            assertNotNull(result.actual());
            assertEquals("Delivered the work", result.actual().actualResult());
        }
    }

    // ── Update Commit ────────────────────────────────────────

    @Nested
    class UpdateCommit {

        @Test
        void updatesAllFieldsInDraftState() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Original"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    "Updated title", "New description",
                    "QUEEN", "OPERATIONS",
                    UUID.randomUUID().toString(), null,
                    "New expected", 0.8, new String[]{"tag1"},
                    "Some notes"
            );

            WeeklyCommitResponse result = commitService.updateCommit(
                    ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID
            );

            assertEquals("Updated title", result.title());
            assertEquals("New description", result.description());
            assertEquals("QUEEN", result.chessPriority());
        }

        @Test
        void allowsProgressNotesUpdateInLockedState() {
            WeeklyPlanEntity plan = lockedPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    null, null, null, null, null, null, null, null, null,
                    "Mid-week update: on track"
            );

            WeeklyCommitResponse result = commitService.updateCommit(
                    ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID
            );

            assertEquals("Mid-week update: on track", result.progressNotes());
        }

        @Test
        void rejectsFrozenFieldUpdateInLockedState() {
            WeeklyPlanEntity plan = lockedPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    "Changed title", null, null, null, null, null, null, null, null, null
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> commitService.updateCommit(ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID)
            );
            assertEquals(ErrorCode.FIELD_FROZEN, ex.getErrorCode());
        }

        @Test
        void rejectsVersionMismatch() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    "Changed", null, null, null, null, null, null, null, null, null
            );

            OptimisticLockException ex = assertThrows(
                    OptimisticLockException.class,
                    () -> commitService.updateCommit(ORG_ID, commit.getId(), 999, request, USER_ID)
            );
            assertEquals(999, ex.getExpectedVersion());
        }

        @Test
        void rejectsUpdateOnReconciledPlan() {
            WeeklyPlanEntity plan = draftPlan();
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    null, null, null, null, null, null, null, null, null,
                    "Notes"
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> commitService.updateCommit(ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID)
            );
            assertEquals(ErrorCode.FIELD_FROZEN, ex.getErrorCode());
        }
    }

    // ── Delete Commit ────────────────────────────────────────

    @Nested
    class DeleteCommit {

        @Test
        void deletesCommitInDraftState() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            commitService.deleteCommit(ORG_ID, commit.getId(), USER_ID);

            verify(commitRepository).delete(commit);
        }

        @Test
        void rejectsDeleteOnLockedPlan() {
            WeeklyPlanEntity plan = lockedPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> commitService.deleteCommit(ORG_ID, commit.getId(), USER_ID)
            );
            assertEquals(ErrorCode.PLAN_NOT_IN_DRAFT, ex.getErrorCode());
        }

        @Test
        void rejectsDeleteOnWrongOrg() {
            UUID otherOrg = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, UUID.randomUUID(), "Task"
            );
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            assertThrows(
                    CommitNotFoundException.class,
                    () -> commitService.deleteCommit(otherOrg, commitId, USER_ID)
            );
            verify(commitRepository, never()).delete(any());
        }
    }

    // ── Audit Events ─────────────────────────────────────────

    @Nested
    class AuditEvents {

        @Test
        void createCommitRecordsAuditAndOutboxEvents() {
            WeeklyPlanEntity plan = draftPlan();
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateCommitRequest request = new CreateCommitRequest(
                    "Audit test commit", null, "KING", "DELIVERY",
                    UUID.randomUUID().toString(), null, null, null, null
            );

            WeeklyCommitResponse result = commitService.createCommit(ORG_ID, plan.getId(), request, USER_ID);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditService).record(
                    eq(ORG_ID), eq(USER_ID),
                    eq(EventType.COMMIT_CREATED.getValue()),
                    eq("WeeklyCommit"), any(UUID.class),
                    eq(null), eq(null), reasonCaptor.capture(), eq(null), eq(null)
            );
            Map<String, Object> auditReason = parseAuditReason(reasonCaptor.getValue());
            assertEquals("Audit test commit", auditReason.get("title"));
            assertEquals(plan.getId().toString(), auditReason.get("planId"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(outboxService).publish(
                    eq(EventType.COMMIT_CREATED), eq("WeeklyCommit"),
                    any(UUID.class), eq(ORG_ID), payloadCaptor.capture()
            );
            Map<String, Object> payload = payloadCaptor.getValue();
            assertEquals("Audit test commit", payload.get("title"));
            assertEquals(plan.getId().toString(), payload.get("planId"));
        }

        @Test
        void updateCommitRecordsAuditAndOutboxEvents() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Original"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    "Updated title", null, null, null, null, null, null, null, null,
                    "Some notes"
            );

            commitService.updateCommit(ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditService).record(
                    eq(ORG_ID), eq(USER_ID),
                    eq(EventType.COMMIT_UPDATED.getValue()),
                    eq("WeeklyCommit"), eq(commit.getId()),
                    eq(null), eq(null), reasonCaptor.capture(), eq(null), eq(null)
            );
            Map<String, Object> auditReason = parseAuditReason(reasonCaptor.getValue());
            @SuppressWarnings("unchecked")
            List<String> auditChangedFields = (List<String>) auditReason.get("changedFields");
            assertTrue(auditChangedFields.contains("title"));
            assertTrue(auditChangedFields.contains("progressNotes"));
            assertEquals("DRAFT", auditReason.get("planState"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(outboxService).publish(
                    eq(EventType.COMMIT_UPDATED), eq("WeeklyCommit"),
                    eq(commit.getId()), eq(ORG_ID), payloadCaptor.capture()
            );
            Map<String, Object> payload = payloadCaptor.getValue();
            @SuppressWarnings("unchecked")
            List<String> changedFields = (List<String>) payload.get("changedFields");
            assertTrue(changedFields.contains("title"));
            assertTrue(changedFields.contains("progressNotes"));
            assertEquals("DRAFT", payload.get("planState"));
        }

        @Test
        void updateCommitAuditOnlyIncludesFieldsThatActuallyChanged() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Original"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    "Original", null, null, null, null, null, null, null, null, ""
            );

            commitService.updateCommit(ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditService).record(
                    eq(ORG_ID), eq(USER_ID),
                    eq(EventType.COMMIT_UPDATED.getValue()),
                    eq("WeeklyCommit"), eq(commit.getId()),
                    eq(null), eq(null), reasonCaptor.capture(), eq(null), eq(null)
            );
            Map<String, Object> auditReason = parseAuditReason(reasonCaptor.getValue());
            @SuppressWarnings("unchecked")
            List<String> changedFields = (List<String>) auditReason.get("changedFields");
            assertTrue(changedFields.isEmpty());
        }

        @Test
        void deleteCommitRecordsAuditAndOutboxEventsWithTitleCapturedBeforeDeletion() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Disappearing work"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            commitService.deleteCommit(ORG_ID, commit.getId(), USER_ID);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditService).record(
                    eq(ORG_ID), eq(USER_ID),
                    eq(EventType.COMMIT_DELETED.getValue()),
                    eq("WeeklyCommit"), eq(commit.getId()),
                    eq(null), eq(null), reasonCaptor.capture(), eq(null), eq(null)
            );
            Map<String, Object> auditReason = parseAuditReason(reasonCaptor.getValue());
            assertEquals(commit.getId().toString(), auditReason.get("commitId"));
            assertEquals("Disappearing work", auditReason.get("title"));
            assertEquals(plan.getId().toString(), auditReason.get("planId"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
            verify(outboxService).publish(
                    eq(EventType.COMMIT_DELETED), eq("WeeklyCommit"),
                    eq(commit.getId()), eq(ORG_ID), payloadCaptor.capture()
            );
            Map<String, Object> payload = payloadCaptor.getValue();
            assertEquals(commit.getId().toString(), payload.get("commitId"));
            assertEquals("Disappearing work", payload.get("title"));
            assertEquals(plan.getId().toString(), payload.get("planId"));
        }

        @Test
        void noAuditEventOnCreateFailure() {
            WeeklyPlanEntity plan = lockedPlan();

            CreateCommitRequest request = new CreateCommitRequest(
                    "New task", null, null, null, null, null, null, null, null
            );

            assertThrows(PlanStateException.class,
                    () -> commitService.createCommit(ORG_ID, plan.getId(), request, USER_ID));

            verify(auditService, never()).record(any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any());
            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }

        @Test
        void noAuditEventOnDeleteFailure() {
            WeeklyPlanEntity plan = lockedPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            assertThrows(PlanStateException.class,
                    () -> commitService.deleteCommit(ORG_ID, commit.getId(), USER_ID));

            verify(auditService, never()).record(any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any());
            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }
    }

    // ── Owner Authorization ─────────────────────────────────

    @Nested
    class OwnerAuthorization {

        @Test
        void createCommitRejectsDifferentUserBeforeStateChecks() {
            WeeklyPlanEntity plan = draftPlan();
            plan.lock(LockType.ON_TIME);

            CreateCommitRequest request = new CreateCommitRequest(
                    "New task", null, null, null, null, null, null, null, null
            );

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> commitService.createCommit(ORG_ID, plan.getId(), request, OTHER_USER)
            );
        }

        @Test
        void updateCommitRejectsDifferentUserBeforeVersionChecks() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    "Changed", null, null, null, null, null, null, null, null, null
            );

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> commitService.updateCommit(ORG_ID, commit.getId(), 999, request, OTHER_USER)
            );
        }

        @Test
        void deleteCommitRejectsDifferentUser() {
            WeeklyPlanEntity plan = draftPlan();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Task"
            );
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> commitService.deleteCommit(ORG_ID, commit.getId(), OTHER_USER)
            );
            verify(commitRepository, never()).delete(any());
        }
    }

    // ── Validation Errors on Draft ───────────────────────────

    @Nested
    class InlineValidation {

        @Test
        void commitWithNoFieldsHasTwoErrors() {
            CommitValidator validator = new CommitValidator();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, UUID.randomUUID(), "Task"
            );

            List<CommitValidationError> errors = validator.validate(commit);

            assertEquals(2, errors.size());
            assertTrue(errors.stream().anyMatch(e -> e.code().equals("MISSING_CHESS_PRIORITY")));
            assertTrue(errors.stream().anyMatch(e -> e.code().equals("MISSING_RCDO_OR_REASON")));
        }

        @Test
        void commitWithOutcomeAndReasonHasConflictError() {
            CommitValidator validator = new CommitValidator();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, UUID.randomUUID(), "Task"
            );
            commit.setChessPriority(ChessPriority.KING);
            commit.setOutcomeId(UUID.randomUUID());
            commit.setNonStrategicReason("Admin work");

            List<CommitValidationError> errors = validator.validate(commit);

            assertEquals(1, errors.size());
            assertEquals("CONFLICTING_LINK", errors.get(0).code());
        }

        @Test
        void validCommitHasNoErrors() {
            CommitValidator validator = new CommitValidator();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, UUID.randomUUID(), "Task"
            );
            commit.setChessPriority(ChessPriority.KING);
            commit.setOutcomeId(UUID.randomUUID());

            List<CommitValidationError> errors = validator.validate(commit);

            assertTrue(errors.isEmpty());
        }

        @Test
        void nonStrategicCommitIsValid() {
            CommitValidator validator = new CommitValidator();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, UUID.randomUUID(), "Admin task"
            );
            commit.setChessPriority(ChessPriority.PAWN);
            commit.setNonStrategicReason("Recurring admin");

            List<CommitValidationError> errors = validator.validate(commit);

            assertTrue(errors.isEmpty());
        }
    }
}
