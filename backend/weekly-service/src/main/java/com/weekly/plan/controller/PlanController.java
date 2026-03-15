package com.weekly.plan.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.plan.dto.CarryForwardRequest;
import com.weekly.plan.dto.CreateReviewRequest;
import com.weekly.plan.dto.ManagerReviewResponse;
import com.weekly.plan.dto.WeeklyPlanResponse;
import com.weekly.plan.service.PlanService;
import com.weekly.plan.service.ReviewService;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for weekly plan lifecycle operations.
 *
 * <p>The {@code userId} and {@code orgId} are sourced exclusively from the
 * validated {@link com.weekly.auth.UserPrincipal} exposed through the
 * request-scoped {@link AuthenticatedUserContext} — never from raw request
 * headers (§9.1).
 */
@RestController
@RequestMapping("/api/v1")
public class PlanController {

    private final PlanService planService;
    private final ReviewService reviewService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public PlanController(
            PlanService planService,
            ReviewService reviewService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.planService = planService;
        this.reviewService = reviewService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @PostMapping("/weeks/{weekStart}/plans")
    public ResponseEntity<WeeklyPlanResponse> createPlan(
            @PathVariable String weekStart
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        PlanService.CreatePlanResult result = planService.createPlan(
                authenticatedUserContext.orgId(), authenticatedUserContext.userId(), weekStartDate
        );

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.plan());
    }

    @GetMapping("/weeks/{weekStart}/plans/me")
    public ResponseEntity<?> getMyPlan(
            @PathVariable String weekStart
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        return planService.getMyPlan(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStartDate
        )
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                        .body(ApiErrorResponse.of(
                                ErrorCode.NOT_FOUND,
                                "Plan not found for user " + authenticatedUserContext.userId()
                                + " and week " + weekStartDate
                        )));
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<?> getPlan(
            @PathVariable UUID planId
    ) {
        return planService.getPlan(authenticatedUserContext.orgId(), planId, authenticatedUserContext.userId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                        .body(ApiErrorResponse.of(ErrorCode.NOT_FOUND, "Plan not found: " + planId)));
    }

    @PostMapping("/plans/{planId}/lock")
    public ResponseEntity<WeeklyPlanResponse> lockPlan(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") int ifMatch
    ) {
        WeeklyPlanResponse response = planService.lockPlan(
                authenticatedUserContext.orgId(),
                planId,
                ifMatch,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plans/{planId}/start-reconciliation")
    public ResponseEntity<WeeklyPlanResponse> startReconciliation(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") int ifMatch
    ) {
        WeeklyPlanResponse response = planService.startReconciliation(
                authenticatedUserContext.orgId(),
                planId,
                ifMatch,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plans/{planId}/submit-reconciliation")
    public ResponseEntity<WeeklyPlanResponse> submitReconciliation(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") int ifMatch
    ) {
        WeeklyPlanResponse response = planService.submitReconciliation(
                authenticatedUserContext.orgId(),
                planId,
                ifMatch,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plans/{planId}/carry-forward")
    public ResponseEntity<WeeklyPlanResponse> carryForward(
            @PathVariable UUID planId,
            @RequestHeader("If-Match") int ifMatch,
            @Valid @RequestBody CarryForwardRequest request
    ) {
        List<UUID> commitIds = request.commitIds().stream()
                .map(UUID::fromString)
                .toList();
        WeeklyPlanResponse response = planService.carryForward(
                authenticatedUserContext.orgId(),
                planId,
                commitIds,
                ifMatch,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/plans/{planId}/review")
    public ResponseEntity<ManagerReviewResponse> submitReview(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        ManagerReviewResponse response = reviewService.submitReview(
                authenticatedUserContext.orgId(),
                planId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.ok(response);
    }
}
