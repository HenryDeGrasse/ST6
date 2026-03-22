package com.weekly.issues.domain;

import com.weekly.plan.domain.ChessPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code issues} table (Phase 6).
 *
 * <p>An Issue is the persistent unit of work that lives in a team's backlog.
 * Issues are created once and referenced by weekly assignments each week they
 * are committed to.
 *
 * <p>Issue key generation is atomic: the service layer must use
 * {@code UPDATE teams SET issue_sequence = issue_sequence + 1 WHERE id = ?
 * RETURNING issue_sequence} to obtain the next sequence number (row-level
 * lock via UPDATE prevents races).
 */
@Entity
@Table(name = "issues")
public class IssueEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    /** Human-readable key, e.g. "PLAT-42". Unique within the org. */
    @Column(name = "issue_key", nullable = false, length = 20, updatable = false)
    private String issueKey;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "effort_type", length = 15)
    private EffortType effortType;

    @Column(name = "estimated_hours", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "chess_priority", length = 10)
    private ChessPriority chessPriority;

    @Column(name = "outcome_id")
    private UUID outcomeId;

    @Column(name = "non_strategic_reason", columnDefinition = "TEXT")
    private String nonStrategicReason;

    @Column(name = "creator_user_id", nullable = false, updatable = false)
    private UUID creatorUserId;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "blocked_by_issue_id")
    private UUID blockedByIssueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IssueStatus status;

    /** AI-computed rank within the team backlog (lower = higher priority). */
    @Column(name = "ai_recommended_rank")
    private Integer aiRecommendedRank;

    @Column(name = "ai_rank_rationale", columnDefinition = "TEXT")
    private String aiRankRationale;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_effort_type", length = 15)
    private EffortType aiSuggestedEffortType;

    /**
     * Incremented whenever the issue's content changes and its Pinecone
     * embedding needs refreshing. The async embedding pipeline compares this
     * to its own stored version to detect stale vectors.
     */
    @Column(name = "embedding_version", nullable = false)
    private int embeddingVersion;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected IssueEntity() {
        // JPA
    }

    public IssueEntity(
            UUID id,
            UUID orgId,
            UUID teamId,
            String issueKey,
            int sequenceNumber,
            String title,
            UUID creatorUserId) {
        this.id = id;
        this.orgId = orgId;
        this.teamId = teamId;
        this.issueKey = issueKey;
        this.sequenceNumber = sequenceNumber;
        this.title = title;
        this.description = "";
        this.status = IssueStatus.OPEN;
        this.creatorUserId = creatorUserId;
        this.embeddingVersion = 0;
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

    public UUID getTeamId() {
        return teamId;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EffortType getEffortType() {
        return effortType;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public ChessPriority getChessPriority() {
        return chessPriority;
    }

    public UUID getOutcomeId() {
        return outcomeId;
    }

    public String getNonStrategicReason() {
        return nonStrategicReason;
    }

    public UUID getCreatorUserId() {
        return creatorUserId;
    }

    public UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    public UUID getBlockedByIssueId() {
        return blockedByIssueId;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public Integer getAiRecommendedRank() {
        return aiRecommendedRank;
    }

    public String getAiRankRationale() {
        return aiRankRationale;
    }

    public EffortType getAiSuggestedEffortType() {
        return aiSuggestedEffortType;
    }

    public int getEmbeddingVersion() {
        return embeddingVersion;
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

    public Instant getArchivedAt() {
        return archivedAt;
    }

    // ── Setters ──────────────────────────────────────────────

    public void setTitle(String title) {
        this.title = title;
        bumpEmbeddingVersion();
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        bumpEmbeddingVersion();
        this.updatedAt = Instant.now();
    }

    public void setEffortType(EffortType effortType) {
        this.effortType = effortType;
        this.updatedAt = Instant.now();
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
        this.updatedAt = Instant.now();
    }

    public void setChessPriority(ChessPriority chessPriority) {
        this.chessPriority = chessPriority;
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

    public void setAssigneeUserId(UUID assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
        this.updatedAt = Instant.now();
    }

    public void setBlockedByIssueId(UUID blockedByIssueId) {
        this.blockedByIssueId = blockedByIssueId;
        this.updatedAt = Instant.now();
    }

    public void setStatus(IssueStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setAiRecommendedRank(Integer aiRecommendedRank) {
        this.aiRecommendedRank = aiRecommendedRank;
        this.updatedAt = Instant.now();
    }

    public void setAiRankRationale(String aiRankRationale) {
        this.aiRankRationale = aiRankRationale;
        this.updatedAt = Instant.now();
    }

    public void setAiSuggestedEffortType(EffortType aiSuggestedEffortType) {
        this.aiSuggestedEffortType = aiSuggestedEffortType;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.status = IssueStatus.ARCHIVED;
        this.archivedAt = Instant.now();
        this.updatedAt = this.archivedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Signals the async embedding pipeline that vectors must be refreshed. */
    private void bumpEmbeddingVersion() {
        this.embeddingVersion++;
    }
}
