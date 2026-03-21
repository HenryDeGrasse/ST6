package com.weekly.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code ai_suggestion_feedback} table.
 *
 * <p>Records user actions on AI-generated next-work suggestions:
 * ACCEPT, DEFER, or DECLINE. A UNIQUE constraint on
 * (org_id, user_id, suggestion_id) enforces one feedback record per
 * user per suggestion.
 */
@Entity
@Table(name = "ai_suggestion_feedback")
public class AiSuggestionFeedbackEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "suggestion_id", nullable = false, updatable = false)
    private UUID suggestionId;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_detail", columnDefinition = "TEXT")
    private String sourceDetail;

    /**
     * Timestamp of the latest feedback action.
     *
     * <p>The suggestion-feedback endpoint uses upsert semantics, so this value is
     * refreshed on updates and doubles as the suppression-window timestamp for
     * recent DECLINE actions.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiSuggestionFeedbackEntity() {
        // JPA
    }

    public AiSuggestionFeedbackEntity(
            UUID id,
            UUID orgId,
            UUID userId,
            UUID suggestionId,
            String action,
            String reason,
            String sourceType,
            String sourceDetail
    ) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.suggestionId = suggestionId;
        this.action = action;
        this.reason = reason;
        this.sourceType = sourceType;
        this.sourceDetail = sourceDetail;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSuggestionId() {
        return suggestionId;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Mutation (used for upsert) ────────────────────────────

    /**
     * Updates the feedback payload for upsert semantics.
     *
     * <p>The action timestamp is refreshed so the 4-week DECLINE suppression
     * window is measured from the user's latest decision, not the original row
     * creation time.
     */
    public void updateFeedback(
            String newAction,
            String newReason,
            String newSourceType,
            String newSourceDetail
    ) {
        this.action = newAction;
        this.reason = newReason;
        this.sourceType = newSourceType;
        this.sourceDetail = newSourceDetail;
        this.createdAt = Instant.now();
    }
}
