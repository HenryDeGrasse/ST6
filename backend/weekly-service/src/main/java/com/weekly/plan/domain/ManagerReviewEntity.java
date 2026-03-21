package com.weekly.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code manager_reviews} table.
 *
 * <p>Records a manager's review decision (APPROVED or CHANGES_REQUESTED)
 * on a weekly plan. Multiple reviews can exist for the same plan
 * (re-reviews after changes are requested).
 */
@Entity
@Table(name = "manager_reviews")
public class ManagerReviewEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "weekly_plan_id", nullable = false, updatable = false)
    private UUID weeklyPlanId;

    @Column(name = "reviewer_user_id", nullable = false, updatable = false)
    private UUID reviewerUserId;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "comments", nullable = false, columnDefinition = "TEXT")
    private String comments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ManagerReviewEntity() {
        // JPA
    }

    public ManagerReviewEntity(
            UUID id, UUID orgId, UUID weeklyPlanId,
            UUID reviewerUserId, String decision, String comments
    ) {
        this.id = id;
        this.orgId = orgId;
        this.weeklyPlanId = weeklyPlanId;
        this.reviewerUserId = reviewerUserId;
        this.decision = decision;
        this.comments = comments;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getWeeklyPlanId() {
        return weeklyPlanId;
    }

    public UUID getReviewerUserId() {
        return reviewerUserId;
    }

    public String getDecision() {
        return decision;
    }

    public String getComments() {
        return comments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
