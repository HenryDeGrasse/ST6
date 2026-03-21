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
 * JPA entity for the {@code progress_entries} table.
 *
 * <p>Represents a single daily check-in micro-update attached to a
 * {@link WeeklyCommitEntity}. Entries are append-only; no update or
 * delete is permitted after creation.
 */
@Entity
@Table(name = "progress_entries")
public class ProgressEntryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "commit_id", nullable = false, updatable = false)
    private UUID commitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 15)
    private ProgressStatus status;

    @Column(name = "note", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProgressEntryEntity() {
        // JPA
    }

    public ProgressEntryEntity(UUID id, UUID orgId, UUID commitId, ProgressStatus status, String note) {
        this.id = id;
        this.orgId = orgId;
        this.commitId = commitId;
        this.status = status;
        this.note = note == null ? "" : note;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getCommitId() {
        return commitId;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
