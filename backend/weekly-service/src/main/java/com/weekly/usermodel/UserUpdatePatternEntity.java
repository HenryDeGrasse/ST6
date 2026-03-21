package com.weekly.usermodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code user_update_patterns} table.
 *
 * <p>Tracks how frequently a given user phrases a check-in note for a
 * particular category. Each unique (orgId, userId, category, noteText)
 * combination has a frequency counter that is incremented each time the
 * same note is submitted. This data feeds the AI check-in option
 * generator, which surfaces the user's most-used phrases as quick-pick
 * options on the Quick Update card.
 */
@Entity
@Table(name = "user_update_patterns")
public class UserUpdatePatternEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category", length = 20)
    private String category;

    @Column(name = "note_text", nullable = false, columnDefinition = "TEXT")
    private String noteText;

    @Column(name = "frequency", nullable = false)
    private int frequency;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserUpdatePatternEntity() {
        // JPA
    }

    /**
     * Creates a new pattern record with a frequency of 1 and timestamps set to now.
     *
     * @param id       the unique ID for this record
     * @param orgId    the organisation this record belongs to
     * @param userId   the user whose note-text habit is being recorded
     * @param category the check-in category (e.g. "ON_TRACK", "BLOCKED")
     * @param noteText the verbatim note text submitted by the user
     */
    public UserUpdatePatternEntity(
            UUID id,
            UUID orgId,
            UUID userId,
            String category,
            String noteText
    ) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.category = category;
        this.noteText = noteText == null ? "" : noteText;
        this.frequency = 1;
        Instant now = Instant.now();
        this.lastUsedAt = now;
        this.createdAt = now;
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

    public String getCategory() {
        return category;
    }

    public String getNoteText() {
        return noteText;
    }

    public int getFrequency() {
        return frequency;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Mutable setters ───────────────────────────────────────

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
