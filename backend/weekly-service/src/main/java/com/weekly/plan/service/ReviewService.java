package com.weekly.plan.service;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ManagerReviewEntity;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ReviewDecision;
import com.weekly.plan.domain.ReviewStatus;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.CreateReviewRequest;
import com.weekly.plan.dto.ManagerReviewResponse;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for manager review operations on weekly plans.
 *
 * <p>Handles approve and changes_requested decisions. Once carry-forward
 * has been executed, only approve and comments are allowed — request
 * changes is blocked (PRD §6).
 */
@Service
public class ReviewService {

    private final WeeklyPlanRepository planRepository;
    private final ManagerReviewRepository reviewRepository;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final OrgGraphClient orgGraphClient;

    public ReviewService(
            WeeklyPlanRepository planRepository,
            ManagerReviewRepository reviewRepository,
            AuditService auditService,
            OutboxService outboxService,
            OrgGraphClient orgGraphClient
    ) {
        this.planRepository = planRepository;
        this.reviewRepository = reviewRepository;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.orgGraphClient = orgGraphClient;
    }

    /**
     * Submits a manager review (APPROVED or CHANGES_REQUESTED).
     *
     * <p>CHANGES_REQUESTED reverts the plan to RECONCILING and sets
     * reviewStatus = CHANGES_REQUESTED (unless carry-forward is already executed).
     *
     * <p>APPROVED sets reviewStatus = APPROVED.
     *
     * @param orgId          the org ID
     * @param planId         the plan to review
     * @param reviewerUserId the manager's user ID
     * @param request        the review decision and comments
     * @return the review record
     */
    @Transactional
    public ManagerReviewResponse submitReview(
            UUID orgId, UUID planId, UUID reviewerUserId, CreateReviewRequest request
    ) {
        WeeklyPlanEntity plan = planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));

        if (!orgGraphClient.isDirectReport(orgId, reviewerUserId, plan.getOwnerUserId())) {
            throw new PlanValidationException(
                    ErrorCode.FORBIDDEN,
                    "Manager access denied for user " + plan.getOwnerUserId(),
                    List.of(Map.of("targetUserId", plan.getOwnerUserId().toString()))
            );
        }

        PlanState state = plan.getState();
        ReviewDecision decision = parseReviewDecision(request.decision());

        // Plan must be RECONCILED or CARRY_FORWARD for review.
        // After changes are requested the IC must resubmit reconciliation,
        // which returns the plan to RECONCILED / REVIEW_PENDING.
        if (state != PlanState.RECONCILED && state != PlanState.CARRY_FORWARD) {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Plan must be in RECONCILED or CARRY_FORWARD state for review",
                    state.name()
            );
        }

        if (decision == ReviewDecision.CHANGES_REQUESTED) {
            // Block changes_requested after carry-forward
            if (plan.getCarryForwardExecutedAt() != null) {
                auditService.record(orgId, reviewerUserId, "review.changes_requested_blocked",
                        "WeeklyPlan", planId, state.name(), state.name(),
                        "Carry-forward already executed; request changes is blocked", null, null);

                throw new PlanStateException(
                        ErrorCode.CARRY_FORWARD_ALREADY_EXECUTED,
                        "Cannot request changes after carry-forward has been executed. "
                                + "Add comments for next week's planning instead.",
                        state.name()
                );
            }

            // Revert plan to RECONCILING
            plan.setState(PlanState.RECONCILING);
            plan.setReviewStatus(ReviewStatus.CHANGES_REQUESTED);
            planRepository.save(plan);

        } else if (decision == ReviewDecision.APPROVED) {
            plan.setReviewStatus(ReviewStatus.APPROVED);
            planRepository.save(plan);
        }

        ManagerReviewEntity review = new ManagerReviewEntity(
                UUID.randomUUID(), orgId, planId, reviewerUserId,
                decision.name(), request.comments()
        );
        reviewRepository.save(review);

        auditService.record(orgId, reviewerUserId, EventType.REVIEW_SUBMITTED.getValue(),
                "WeeklyPlan", planId, null, decision.name(),
                request.comments(), null, null);

        outboxService.publish(EventType.REVIEW_SUBMITTED, "WeeklyPlan", planId, orgId,
                Map.of("reviewerUserId", reviewerUserId.toString(),
                        "decision", decision.name(),
                        "ownerUserId", plan.getOwnerUserId().toString(),
                        "weekStartDate", plan.getWeekStartDate().toString()));

        return ManagerReviewResponse.from(review);
    }

    private ReviewDecision parseReviewDecision(String rawDecision) {
        if (rawDecision == null || rawDecision.isBlank()) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request validation failed",
                    List.of(Map.of(
                            "field", "decision",
                            "message", "must not be blank"
                    ))
            );
        }

        try {
            return ReviewDecision.valueOf(rawDecision);
        } catch (IllegalArgumentException ex) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request validation failed",
                    List.of(Map.of(
                            "field", "decision",
                            "message", "must be one of APPROVED, CHANGES_REQUESTED"
                    ))
            );
        }
    }

    /**
     * Lists all reviews for a plan.
     */
    @Transactional(readOnly = true)
    public List<ManagerReviewResponse> listReviews(UUID orgId, UUID planId) {
        planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));

        return reviewRepository.findByOrgIdAndWeeklyPlanId(orgId, planId)
                .stream()
                .map(ManagerReviewResponse::from)
                .toList();
    }
}
