package com.weekly.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code weekly_commit_actuals} table.
 *
 * <p>Stores reconciliation data (actual result, completion status, delta reason)
 * for a single commitment. The aggregate root is {@link WeeklyCommitEntity};
 * writing actuals increments the commit's version (optimistic lock is on the commit).
 */
@Entity
@Table(name = "weekly_commit_actuals")
public class WeeklyCommitActualEntity {

    @Id
    @Column(name = "commit_id", nullable = false, updatable = false)
    private UUID commitId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "actual_result", nullable = false, columnDefinition = "TEXT")
    private String actualResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_status", nullable = false, length = 15)
    private CompletionStatus completionStatus;

    @Column(name = "delta_reason", columnDefinition = "TEXT")
    private String deltaReason;

    @Column(name = "time_spent")
    private Integer timeSpent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyCommitActualEntity() {
        // JPA
    }

    public WeeklyCommitActualEntity(UUID commitId, UUID orgId) {
        this.commitId = commitId;
        this.orgId = orgId;
        this.actualResult = "";
        this.completionStatus = CompletionStatus.NOT_DONE;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getCommitId() {
        return commitId;
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

    public Integer getTimeSpent() {
        return timeSpent;
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

    public void setTimeSpent(Integer timeSpent) {
        this.timeSpent = timeSpent;
        this.updatedAt = Instant.now();
    }
}
