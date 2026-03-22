package com.weekly.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code teams} table (Phase 6).
 *
 * <p>A team owns a set of backlog issues and provides a shared pool of work
 * for its members. Issue keys are prefixed with the team's {@code keyPrefix}
 * (e.g. "PLAT-42"). The {@code issueSequence} counter is incremented
 * atomically via {@code UPDATE teams SET issue_sequence = issue_sequence + 1
 * WHERE id = ? RETURNING issue_sequence} to guarantee uniqueness under load.
 */
@Entity
@Table(name = "teams")
public class TeamEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 10, updatable = false)
    private String keyPrefix;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    /** Current highest sequence number; incremented atomically at the DB level. */
    @Column(name = "issue_sequence", nullable = false)
    private int issueSequence;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TeamEntity() {
        // JPA
    }

    public TeamEntity(UUID id, UUID orgId, String name, String keyPrefix, UUID ownerUserId) {
        this.id = id;
        this.orgId = orgId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.description = "";
        this.ownerUserId = ownerUserId;
        this.issueSequence = 0;
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

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public int getIssueSequence() {
        return issueSequence;
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

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
        this.updatedAt = Instant.now();
    }

    public void setIssueSequence(int issueSequence) {
        this.issueSequence = issueSequence;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
