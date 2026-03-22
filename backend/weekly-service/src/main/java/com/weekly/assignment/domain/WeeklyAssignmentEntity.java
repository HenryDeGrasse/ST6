package com.weekly.assignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code weekly_assignments} table (Phase 6).
 *
 * <p>A weekly assignment links a persistent {@link com.weekly.issues.domain.IssueEntity}
 * to a specific weekly plan. It replaces the ephemeral {@code weekly_commits} as the
 * primary work unit in Phase 6, while the old commits table is preserved during
 * the Phase A additive migration.
 *
 * <p>The {@code legacyCommitId} crosswalk column links back to the legacy
 * {@code weekly_commits} row this assignment mirrors, enabling dual-read
 * compatibility during the transition period.
 */
@Entity
@Table(name = "weekly_assignments")
public class WeeklyAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "weekly_plan_id", nullable = false, updatable = false)
    private UUID weeklyPlanId;

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "chess_priority_override", length = 10)
    private String chessPriorityOverride;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    // ── RCDO snapshot ────────────────────────────────────────

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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false)
    private String[] tags;

    /**
     * Crosswalk to the legacy {@code weekly_commits} row this assignment mirrors.
     * Null when the assignment was created directly (not migrated from a commit).
     */
    @Column(name = "legacy_commit_id")
    private UUID legacyCommitId;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WeeklyAssignmentEntity() {
        // JPA
    }

    public WeeklyAssignmentEntity(UUID id, UUID orgId, UUID weeklyPlanId, UUID issueId) {
        this.id = id;
        this.orgId = orgId;
        this.weeklyPlanId = weeklyPlanId;
        this.issueId = issueId;
        this.expectedResult = "";
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

    public UUID getIssueId() {
        return issueId;
    }

    public String getChessPriorityOverride() {
        return chessPriorityOverride;
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public BigDecimal getConfidence() {
        return confidence;
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

    public String[] getTags() {
        return tags == null ? new String[0] : Arrays.copyOf(tags, tags.length);
    }

    public UUID getLegacyCommitId() {
        return legacyCommitId;
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

    // ── Setters ──────────────────────────────────────────────

    public void setChessPriorityOverride(String chessPriorityOverride) {
        this.chessPriorityOverride = chessPriorityOverride;
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

    public void setTagsFromArray(String[] tags) {
        this.tags = tags == null ? new String[0] : Arrays.copyOf(tags, tags.length);
        this.updatedAt = Instant.now();
    }

    public void setLegacyCommitId(UUID legacyCommitId) {
        this.legacyCommitId = legacyCommitId;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void populateSnapshot(
            UUID rallyCryId, String rallyCryName,
            UUID objectiveId, String objectiveName,
            UUID outcomeId, String outcomeName) {
        this.snapshotRallyCryId = rallyCryId;
        this.snapshotRallyCryName = rallyCryName;
        this.snapshotObjectiveId = objectiveId;
        this.snapshotObjectiveName = objectiveName;
        this.snapshotOutcomeId = outcomeId;
        this.snapshotOutcomeName = outcomeName;
    }
}
