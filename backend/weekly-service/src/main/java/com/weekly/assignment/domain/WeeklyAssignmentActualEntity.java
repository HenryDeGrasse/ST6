package com.weekly.assignment.domain;

import com.weekly.plan.domain.CompletionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code weekly_assignment_actuals} table (Phase 6).
 *
 * <p>Records the outcome of a {@link WeeklyAssignmentEntity} after reconciliation.
 * One-to-one with the assignment (PK = assignment ID).
 */
@Entity
@Table(name = "weekly_assignment_actuals")
public class WeeklyAssignmentActualEntity {

    @Id
    @Column(name = "assignment_id", nullable = false, updatable = false)
    private UUID assignmentId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "actual_result", nullable = false, columnDefinition = "TEXT")
    private String actualResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status", nullable = false, length = 15)
    private CompletionStatus completionStatus;

    @Column(name = "delta_reason", columnDefinition = "TEXT")
    private String deltaReason;

    @Column(name = "hours_spent", precision = 6, scale = 2)
    private BigDecimal hoursSpent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyAssignmentActualEntity() {
        // JPA
    }

    public WeeklyAssignmentActualEntity(
            UUID assignmentId,
            UUID orgId,
            CompletionStatus completionStatus) {
        this.assignmentId = assignmentId;
        this.orgId = orgId;
        this.actualResult = "";
        this.completionStatus = completionStatus;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getActualResult() {
        return actualResult;
    }

    public CompletionStatus getCompletionStatus() {
        return completionStatus;
    }

    public String getDeltaReason() {
        return deltaReason;
    }

    public BigDecimal getHoursSpent() {
        return hoursSpent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ── Setters ──────────────────────────────────────────────

    public void setActualResult(String actualResult) {
        this.actualResult = actualResult;
        this.updatedAt = Instant.now();
    }

    public void setCompletionStatus(CompletionStatus completionStatus) {
        this.completionStatus = completionStatus;
        this.updatedAt = Instant.now();
    }

    public void setDeltaReason(String deltaReason) {
        this.deltaReason = deltaReason;
        this.updatedAt = Instant.now();
    }

    public void setHoursSpent(BigDecimal hoursSpent) {
        this.hoursSpent = hoursSpent;
        this.updatedAt = Instant.now();
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
