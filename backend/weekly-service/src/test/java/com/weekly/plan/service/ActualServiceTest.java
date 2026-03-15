package com.weekly.plan.service;

import com.weekly.audit.AuditService;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.UpdateActualRequest;
import com.weekly.plan.dto.WeeklyCommitActualResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActualService}: actuals editing with state enforcement.
 */
class ActualServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private AuditService auditService;
    private OutboxService outboxService;
    private ActualService actualService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        auditService = mock(AuditService.class);
        outboxService = mock(OutboxService.class);
        actualService = new ActualService(
                planRepository, commitRepository, actualRepository,
                auditService, outboxService
        );
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private WeeklyPlanEntity reconcilingPlan(UUID planId) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
        plan.lock(LockType.ON_TIME);
        plan.startReconciliation();
        return plan;
    }

    @Nested
    class UpdateActual {

        @Test
        void rejectsDifferentUserBeforeVersionChecks() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = reconcilingPlan(planId);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task");
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateActualRequest request = new UpdateActualRequest(
                    "Done", "DONE", null, null
            );

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> actualService.updateActual(ORG_ID, commit.getId(), 999, request, OTHER_USER)
            );
        }

        @Test
        void createsNewActualForCommit() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = reconcilingPlan(planId);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task");
            commit.setChessPriority(ChessPriority.KING);
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(actualRepository.findById(commit.getId())).thenReturn(Optional.empty());
            when(actualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateActualRequest request = new UpdateActualRequest(
                    "Feature shipped", "DONE", null, 120
            );

            WeeklyCommitActualResponse result = actualService.updateActual(
                    ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID
            );

            assertEquals("DONE", result.completionStatus());
            assertEquals("Feature shipped", result.actualResult());
            assertEquals(120, result.timeSpent());
            verify(actualRepository).save(any(WeeklyCommitActualEntity.class));
        }

        @Test
        void updatesExistingActual() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = reconcilingPlan(planId);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task");
            commit.setChessPriority(ChessPriority.KING);
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WeeklyCommitActualEntity existing = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            existing.setCompletionStatus(CompletionStatus.NOT_DONE);
            when(actualRepository.findById(commit.getId())).thenReturn(Optional.of(existing));
            when(actualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateActualRequest request = new UpdateActualRequest(
                    "Partially done", "PARTIALLY", "Blocked by dependency", 60
            );

            WeeklyCommitActualResponse result = actualService.updateActual(
                    ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID
            );

            assertEquals("PARTIALLY", result.completionStatus());
            assertEquals("Blocked by dependency", result.deltaReason());
        }

        @Test
        void rejectsActualUpdateOnLockedPlan() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            plan.lock(LockType.ON_TIME);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task");
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateActualRequest request = new UpdateActualRequest(
                    "Done", "DONE", null, null
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> actualService.updateActual(ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }

        @Test
        void rejectsActualUpdateOnDraftPlan() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task");
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateActualRequest request = new UpdateActualRequest(
                    "Done", "DONE", null, null
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> actualService.updateActual(ORG_ID, commit.getId(), commit.getVersion(), request, USER_ID)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }

        @Test
        void rejectsVersionMismatch() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = reconcilingPlan(planId);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task");
            when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

            UpdateActualRequest request = new UpdateActualRequest(
                    "Done", "DONE", null, null
            );

            assertThrows(OptimisticLockException.class,
                    () -> actualService.updateActual(ORG_ID, commit.getId(), 999, request, USER_ID));
        }
    }
}
