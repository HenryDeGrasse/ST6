package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditService;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitValidator;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanService;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlanningCopilotDraftApplyService}.
 */
class PlanningCopilotDraftApplyServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID REPORT_ID = UUID.randomUUID();

    private OrgGraphClient orgGraphClient;
    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private CommitValidator commitValidator;
    private PlanService planService;
    private AuditService auditService;
    private OutboxService outboxService;
    private PlanningCopilotDraftApplyService service;

    @BeforeEach
    void setUp() {
        orgGraphClient = mock(OrgGraphClient.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        commitValidator = new CommitValidator();
        planService = mock(PlanService.class);
        auditService = mock(AuditService.class);
        outboxService = mock(OutboxService.class);
        service = new PlanningCopilotDraftApplyService(
                orgGraphClient,
                weeklyPlanRepository,
                weeklyCommitRepository,
                commitValidator,
                planService,
                auditService,
                outboxService);
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    @Test
    void applyCreatesAiPlannedDraftCommitsForDirectReport() {
        LocalDate weekStart = currentMonday();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, REPORT_ID, weekStart);

        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                .thenReturn(List.of(new DirectReport(REPORT_ID, "Alex")));
        WeeklyCommitEntity persistedCommit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, plan.getId(), "Advance healthcare milestone");
        persistedCommit.setDescription("Critical outcome");
        persistedCommit.setChessPriority(com.weekly.plan.domain.ChessPriority.KING);
        persistedCommit.setOutcomeId(UUID.randomUUID());
        persistedCommit.setEstimatedHours(java.math.BigDecimal.valueOf(8.0));
        persistedCommit.setTagsFromArray(new String[]{PlanningCopilotDraftApplyService.AI_PLANNED_TAG});

        when(planService.createPlan(ORG_ID, REPORT_ID, weekStart))
                .thenReturn(new PlanService.CreatePlanResult(com.weekly.plan.dto.WeeklyPlanResponse.from(plan), true));
        when(weeklyPlanRepository.findByOrgIdAndId(ORG_ID, plan.getId())).thenReturn(Optional.of(plan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                .thenReturn(List.of())
                .thenReturn(List.of(persistedCommit));
        when(weeklyCommitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(weeklyPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlanningCopilotDraftApplyService.ApplyTeamPlanSuggestionResult result = service.apply(
                ORG_ID,
                MANAGER_ID,
                weekStart,
                List.of(new PlanningCopilotController.TeamMemberApplyRequest(
                        REPORT_ID.toString(),
                        List.of(new PlanningCopilotController.SuggestedCommitApplyRequest(
                                "Advance healthcare milestone",
                                UUID.randomUUID().toString(),
                                "Critical outcome",
                                "KING",
                                8.0)))));

        assertEquals("ok", result.status());
        assertEquals(1, result.members().size());
        assertTrue(result.members().getFirst().createdPlan());
        assertEquals(1, result.members().getFirst().appliedCommits().size());
        assertEquals("Advance healthcare milestone", result.members().getFirst().appliedCommits().getFirst().title());
        assertEquals("Critical outcome", result.members().getFirst().appliedCommits().getFirst().description());
    }

    @Test
    void applyRejectsNonDirectReport() {
        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID)).thenReturn(List.of());

        assertThrows(PlanAccessForbiddenException.class, () -> service.apply(
                ORG_ID,
                MANAGER_ID,
                currentMonday(),
                List.of(new PlanningCopilotController.TeamMemberApplyRequest(
                        REPORT_ID.toString(),
                        List.of()))));

        verify(planService, never()).createPlan(any(), any(), any());
    }

    @Test
    void applyRejectsNonDraftPlan() {
        LocalDate weekStart = currentMonday();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, REPORT_ID, weekStart);
        plan.setState(PlanState.LOCKED);

        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                .thenReturn(List.of(new DirectReport(REPORT_ID, "Alex")));
        when(planService.createPlan(ORG_ID, REPORT_ID, weekStart))
                .thenReturn(new PlanService.CreatePlanResult(com.weekly.plan.dto.WeeklyPlanResponse.from(plan), false));
        when(weeklyPlanRepository.findByOrgIdAndId(ORG_ID, plan.getId())).thenReturn(Optional.of(plan));

        assertThrows(PlanStateException.class, () -> service.apply(
                ORG_ID,
                MANAGER_ID,
                weekStart,
                List.of(new PlanningCopilotController.TeamMemberApplyRequest(
                        REPORT_ID.toString(),
                        List.of()))));
    }

    @Test
    void applyReplacesExistingAiPlannedCommitsOnly() {
        LocalDate weekStart = currentMonday();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, REPORT_ID, weekStart);
        WeeklyCommitEntity aiCommit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, plan.getId(), "Old AI task");
        aiCommit.setTagsFromArray(new String[]{PlanningCopilotDraftApplyService.AI_PLANNED_TAG});
        WeeklyCommitEntity manualCommit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, plan.getId(), "Manual task");
        manualCommit.setTagsFromArray(new String[]{"customer"});
        WeeklyCommitEntity savedCommit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, plan.getId(), "New AI task");
        savedCommit.setTagsFromArray(new String[]{PlanningCopilotDraftApplyService.AI_PLANNED_TAG});
        savedCommit.setDescription("Fresh rationale");

        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                .thenReturn(List.of(new DirectReport(REPORT_ID, "Alex")));
        when(planService.createPlan(ORG_ID, REPORT_ID, weekStart))
                .thenReturn(new PlanService.CreatePlanResult(com.weekly.plan.dto.WeeklyPlanResponse.from(plan), false));
        when(weeklyPlanRepository.findByOrgIdAndId(ORG_ID, plan.getId())).thenReturn(Optional.of(plan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                .thenReturn(List.of(aiCommit, manualCommit))
                .thenReturn(List.of(manualCommit, savedCommit));
        when(weeklyCommitRepository.save(any())).thenAnswer(inv -> {
            WeeklyCommitEntity commit = inv.getArgument(0);
            commit.setTagsFromArray(new String[]{PlanningCopilotDraftApplyService.AI_PLANNED_TAG});
            return commit;
        });
        when(weeklyPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlanningCopilotDraftApplyService.ApplyTeamPlanSuggestionResult result = service.apply(
                ORG_ID,
                MANAGER_ID,
                weekStart,
                List.of(new PlanningCopilotController.TeamMemberApplyRequest(
                        REPORT_ID.toString(),
                        List.of(new PlanningCopilotController.SuggestedCommitApplyRequest(
                                "New AI task",
                                null,
                                "Fresh rationale",
                                "QUEEN",
                                5.0)))));

        assertFalse(result.members().getFirst().createdPlan());
        assertEquals(1, result.members().getFirst().appliedCommits().size());
        verify(weeklyCommitRepository).delete(eq(aiCommit));
        verify(weeklyCommitRepository, never()).delete(eq(manualCommit));
    }

    @Test
    void applyRejectsInvalidUserId() {
        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                .thenReturn(List.of(new DirectReport(REPORT_ID, "Alex")));

        PlanValidationException ex = assertThrows(PlanValidationException.class, () -> service.apply(
                ORG_ID,
                MANAGER_ID,
                currentMonday(),
                List.of(new PlanningCopilotController.TeamMemberApplyRequest(
                        "not-a-uuid",
                        List.of()))));

        assertEquals("userId must be a UUID string", ex.getMessage());
        assertEquals(com.weekly.shared.ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(planService, never()).createPlan(any(), any(), any());
    }

    @Test
    void applyRejectsDuplicateUserIds() {
        when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                .thenReturn(List.of(new DirectReport(REPORT_ID, "Alex")));

        PlanValidationException ex = assertThrows(PlanValidationException.class, () -> service.apply(
                ORG_ID,
                MANAGER_ID,
                currentMonday(),
                List.of(
                        new PlanningCopilotController.TeamMemberApplyRequest(REPORT_ID.toString(), List.of()),
                        new PlanningCopilotController.TeamMemberApplyRequest(REPORT_ID.toString(), List.of()))));

        assertEquals("members must not contain duplicate userId values", ex.getMessage());
        assertEquals(com.weekly.shared.ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(planService, never()).createPlan(any(), any(), any());
    }
}
