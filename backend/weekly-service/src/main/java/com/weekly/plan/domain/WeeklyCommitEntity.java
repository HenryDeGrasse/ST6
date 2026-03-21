package com.weekly.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code weekly_commits} table.
 *
 * <p>Represents a single commitment within a weekly plan. During DRAFT,
 * all planning fields are mutable. After LOCK, only {@code progressNotes}
 * may be updated.
 *
 * <p>Soft-deleted commits ({@code deleted_at IS NOT NULL}) are hidden from all
 * normal queries via the {@link SQLRestriction} filter (PRD §14.7).
 */
@Entity
@Table(name = "weekly_commits")
@SQLRestriction("deleted_at IS NULL")
public class WeeklyCommitEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "weekly_plan_id", nullable = false, updatable = false)
    private UUID weeklyPlanId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "chess_priority", length = 10)
    private ChessPriority chessPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private CommitCategory category;

    @Column(name = "outcome_id")
    private UUID outcomeId;

    @Column(name = "non_strategic_reason", columnDefinition = "TEXT")
    private String nonStrategicReason;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "estimated_hours", precision = 5, scale = 1)
    private BigDecimal estimatedHours;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false)
    private String[] tags;

    @Column(name = "progress_notes", nullable = false, columnDefinition = "TEXT")
    private String progressNotes;

    // ── RCDO snapshot (populated at lock time) ──────────────

    @Column(name = "snapshot_rally_cry_id")
    private UUID snapshotRallyCryId;

    @Column(name = "snapshot_rally_cry_name", length = 500)
    private String snapshotRallyCryName;

    @Column(name = "snapshot_objective_id")
    private UUID snapshotObjectiveId;

    @Column(name = "snapshot_objective_name", length = 500)
    private String snapshotObjectiveName;

    @Column(name = "snapshot_outcome_id")
    private UUID snapshotOutcomeId;

    @Column(name = "snapshot_outcome_name", length = 500)
    private String snapshotOutcomeName;

    // ── Carry-forward lineage ───────────────────────────────

    @Column(name = "carried_from_commit_id")
    private UUID carriedFromCommitId;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Set by {@link com.weekly.plan.service.PlanRetentionJob} when the commit is soft-deleted. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected WeeklyCommitEntity() {
        // JPA
    }

    public WeeklyCommitEntity(UUID id, UUID orgId, UUID weeklyPlanId, String title) {
        this.id = id;
        this.orgId = orgId;
        this.weeklyPlanId = weeklyPlanId;
        this.title = title;
        this.description = "";
        this.expectedResult = "";
        this.progressNotes = "";
        this.tags = new String[0];
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

    public UUID getWeeklyPlanId() {
        return weeklyPlanId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ChessPriority getChessPriority() {
        return chessPriority;
    }

    public CommitCategory getCategory() {
        return category;
    }

    public UUID getOutcomeId() {
        return outcomeId;
    }

    public String getNonStrategicReason() {
        return nonStrategicReason;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public String[] getTags() {
        return tags == null ? new String[0] : Arrays.copyOf(tags, tags.length);
    }

    public String getProgressNotes() {
        return progressNotes;
    }

    public UUID getSnapshotRallyCryId() {
        return snapshotRallyCryId;
    }

    public String getSnapshotRallyCryName() {
        return snapshotRallyCryName;
    }

    public UUID getSnapshotObjectiveId() {
        return snapshotObjectiveId;
    }

    public String getSnapshotObjectiveName() {
        return snapshotObjectiveName;
    }

    public UUID getSnapshotOutcomeId() {
        return snapshotOutcomeId;
    }

    public String getSnapshotOutcomeName() {
        return snapshotOutcomeName;
    }

    public UUID getCarriedFromCommitId() {
        return carriedFromCommitId;
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

    // ── Setters (used by service layer) ─────────────────────

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void setChessPriority(ChessPriority chessPriority) {
        this.chessPriority = chessPriority;
        this.updatedAt = Instant.now();
    }

    public void setCategory(CommitCategory category) {
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public void setOutcomeId(UUID outcomeId) {
        this.outcomeId = outcomeId;
        this.updatedAt = Instant.now();
    }

    public void setNonStrategicReason(String nonStrategicReason) {
        this.nonStrategicReason = nonStrategicReason;
        this.updatedAt = Instant.now();
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult;
        this.updatedAt = Instant.now();
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
        this.updatedAt = Instant.now();
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
        this.updatedAt = Instant.now();
    }

    public void setTagsFromArray(String[] tags) {
        this.tags = tags == null ? new String[0] : Arrays.copyOf(tags, tags.length);
        this.updatedAt = Instant.now();
    }

    public void setProgressNotes(String progressNotes) {
        this.progressNotes = progressNotes;
        this.updatedAt = Instant.now();
    }

    public void setCarriedFromCommitId(UUID carriedFromCommitId) {
        this.carriedFromCommitId = carriedFromCommitId;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    // ── RCDO snapshot population ────────────────────────────

    public void populateSnapshot(
            UUID rallyCryId, String rallyCryName,
            UUID objectiveId, String objectiveName,
            UUID outcomeIdVal, String outcomeName
    ) {
        this.snapshotRallyCryId = rallyCryId;
        this.snapshotRallyCryName = rallyCryName;
        this.snapshotObjectiveId = objectiveId;
        this.snapshotObjectiveName = objectiveName;
        this.snapshotOutcomeId = outcomeIdVal;
        this.snapshotOutcomeName = outcomeName;
    }
}
