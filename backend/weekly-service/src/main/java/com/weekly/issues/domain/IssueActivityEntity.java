package com.weekly.issues.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code issue_activities} table (Phase 6).
 *
 * <p>Append-only audit log for every meaningful change to an {@link IssueEntity}.
 * The polymorphic payload columns ({@code oldValue}, {@code newValue},
 * {@code commentText}, {@code hoursLogged}, {@code metadata}) are populated
 * based on the activity type.
 */
@Entity
@Table(name = "issue_activities")
public class IssueActivityEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30, updatable = false)
    private IssueActivityType activityType;

    @Column(name = "old_value", columnDefinition = "TEXT", updatable = false)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT", updatable = false)
    private String newValue;

    @Column(name = "comment_text", columnDefinition = "TEXT", updatable = false)
    private String commentText;

    @Column(name = "hours_logged", precision = 6, scale = 2, updatable = false)
    private BigDecimal hoursLogged;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "JSONB", updatable = false)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IssueActivityEntity() {
        // JPA
    }

    public IssueActivityEntity(
            UUID id,
            UUID orgId,
            UUID issueId,
            UUID actorUserId,
            IssueActivityType activityType) {
        this.id = id;
        this.orgId = orgId;
        this.issueId = issueId;
        this.actorUserId = actorUserId;
        this.activityType = activityType;
        this.metadata = "{}";
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public IssueActivityType getActivityType() {
        return activityType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getCommentText() {
        return commentText;
    }

    public BigDecimal getHoursLogged() {
        return hoursLogged;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Builder-style setters (used before persist) ──────────

    public IssueActivityEntity withChange(String oldVal, String newVal) {
        this.oldValue = oldVal;
        this.newValue = newVal;
        return this;
    }

    public IssueActivityEntity withComment(String comment) {
        this.commentText = comment;
        return this;
    }

    public IssueActivityEntity withHours(BigDecimal hours) {
        this.hoursLogged = hours;
        return this;
    }

    public IssueActivityEntity withMetadata(String jsonMetadata) {
        this.metadata = jsonMetadata;
        return this;
    }
}
