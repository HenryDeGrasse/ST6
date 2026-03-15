package com.weekly.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the {@code weekly_plans} table.
 *
 * <p>Represents a single user's plan for a given week. The plan follows
 * a lifecycle state machine: DRAFT → LOCKED → RECONCILING → RECONCILED → CARRY_FORWARD.
 */
@Entity
@Table(name = "weekly_plans")
public class WeeklyPlanEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "week_start_date", nullable = false, updatable = false)
    private LocalDate weekStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private PlanState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type", length = 15)
    private LockType lockType;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "carry_forward_executed_at")
    private Instant carryForwardExecutedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyPlanEntity() {
        // JPA
    }

    public WeeklyPlanEntity(UUID id, UUID orgId, UUID ownerUserId, LocalDate weekStartDate) {
        this.id = id;
        this.orgId = orgId;
        this.ownerUserId = ownerUserId;
        this.weekStartDate = weekStartDate;
        this.state = PlanState.DRAFT;
        this.reviewStatus = ReviewStatus.REVIEW_NOT_APPLICABLE;
        this.version = 1;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public PlanState getState() {
        return state;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public LockType getLockType() {
        return lockType;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getCarryForwardExecutedAt() {
        return carryForwardExecutedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ── State transitions ────────────────────────────────────

    public void lock(LockType type) {
        this.state = PlanState.LOCKED;
        this.lockType = type;
        this.lockedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void startReconciliation() {
        this.state = PlanState.RECONCILING;
        this.updatedAt = Instant.now();
    }

    public void submitReconciliation() {
        this.state = PlanState.RECONCILED;
        this.reviewStatus = ReviewStatus.REVIEW_PENDING;
        this.updatedAt = Instant.now();
    }

    public void carryForward() {
        this.state = PlanState.CARRY_FORWARD;
        this.carryForwardExecutedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void setState(PlanState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
        this.updatedAt = Instant.now();
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
