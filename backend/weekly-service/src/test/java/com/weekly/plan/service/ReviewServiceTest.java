package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditService;
import com.weekly.auth.InMemoryOrgGraphClient;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ReviewStatus;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.CreateReviewRequest;
import com.weekly.plan.dto.ManagerReviewResponse;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReviewService}: manager review with carry-forward restrictions.
 */
class ReviewServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private ManagerReviewRepository reviewRepository;
    private AuditService auditService;
    private OutboxService outboxService;
    private InMemoryOrgGraphClient orgGraphClient;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        reviewRepository = mock(ManagerReviewRepository.class);
        auditService = mock(AuditService.class);
        outboxService = mock(OutboxService.class);
        orgGraphClient = new InMemoryOrgGraphClient();
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, java.util.List.of(USER_ID));
        reviewService = new ReviewService(
                planRepository,
                reviewRepository,
                auditService,
                outboxService,
                orgGraphClient
        );
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private WeeklyPlanEntity reconciledPlan() {
        UUID planId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
        plan.lock(LockType.ON_TIME);
        plan.startReconciliation();
        plan.submitReconciliation();
        when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return plan;
    }

    @Nested
    class ApproveReview {

        @Test
        void approvesReconciledPlan() {
            WeeklyPlanEntity plan = reconciledPlan();
            CreateReviewRequest request = new CreateReviewRequest("APPROVED", "Looks good!");

            ManagerReviewResponse result = reviewService.submitReview(
                    ORG_ID, plan.getId(), MANAGER_ID, request
            );

            assertEquals("APPROVED", result.decision());
            assertEquals("Looks good!", result.comments());
            assertEquals(ReviewStatus.APPROVED, plan.getReviewStatus());
        }

        @Test
        void approvesAfterCarryForward() {
            WeeklyPlanEntity plan = reconciledPlan();
            plan.carryForward(); // CARRY_FORWARD state
            when(planRepository.findByOrgIdAndId(ORG_ID, plan.getId())).thenReturn(Optional.of(plan));

            CreateReviewRequest request = new CreateReviewRequest("APPROVED", "Approved post-carry-forward");

            ManagerReviewResponse result = reviewService.submitReview(
                    ORG_ID, plan.getId(), MANAGER_ID, request
            );

            assertEquals("APPROVED", result.decision());
        }
    }

    @Nested
    class ChangesRequested {

        @Test
        void requestsChangesOnReconciledPlan() {
            WeeklyPlanEntity plan = reconciledPlan();
            CreateReviewRequest request = new CreateReviewRequest(
                    "CHANGES_REQUESTED", "Please update delta reasons"
            );

            ManagerReviewResponse result = reviewService.submitReview(
                    ORG_ID, plan.getId(), MANAGER_ID, request
            );

            assertEquals("CHANGES_REQUESTED", result.decision());
            assertEquals(PlanState.RECONCILING, plan.getState());
            assertEquals(ReviewStatus.CHANGES_REQUESTED, plan.getReviewStatus());
        }

        @Test
        void blocksChangesRequestedAfterCarryForward() {
            WeeklyPlanEntity plan = reconciledPlan();
            plan.carryForward(); // carryForwardExecutedAt is now set
            when(planRepository.findByOrgIdAndId(ORG_ID, plan.getId())).thenReturn(Optional.of(plan));

            CreateReviewRequest request = new CreateReviewRequest(
                    "CHANGES_REQUESTED", "Too late for changes"
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> reviewService.submitReview(ORG_ID, plan.getId(), MANAGER_ID, request)
            );
            assertEquals(ErrorCode.CARRY_FORWARD_ALREADY_EXECUTED, ex.getErrorCode());
            verify(auditService).record(
                    ORG_ID,
                    MANAGER_ID,
                    "review.changes_requested_blocked",
                    "WeeklyPlan",
                    plan.getId(),
                    PlanState.CARRY_FORWARD.name(),
                    PlanState.CARRY_FORWARD.name(),
                    "Carry-forward already executed; request changes is blocked",
                    null,
                    null
            );
        }
    }

    @Nested
    class Authorization {

        @Test
        void rejectsReviewerWhoIsNotDirectManager() {
            WeeklyPlanEntity plan = reconciledPlan();
            UUID otherManagerId = UUID.randomUUID();
            CreateReviewRequest request = new CreateReviewRequest("APPROVED", "Looks good");

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> reviewService.submitReview(ORG_ID, plan.getId(), otherManagerId, request)
            );

            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }

    @Nested
    class InvalidStates {

        @Test
        void rejectsReviewOnDraftPlan() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            CreateReviewRequest request = new CreateReviewRequest("APPROVED", "Too early");

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> reviewService.submitReview(ORG_ID, planId, MANAGER_ID, request)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }

        @Test
        void rejectsReviewOnLockedPlan() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            plan.lock(LockType.ON_TIME);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            CreateReviewRequest request = new CreateReviewRequest("APPROVED", "Not yet reconciled");

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> reviewService.submitReview(ORG_ID, planId, MANAGER_ID, request)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }
    }
}
