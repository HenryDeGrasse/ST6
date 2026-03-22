package com.weekly.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code team_access_requests} table (Phase 6).
 *
 * <p>Records requests from users to join a team. The team owner approves
 * or denies requests; approval automatically creates a {@link TeamMemberEntity}.
 */
@Entity
@Table(name = "team_access_requests")
public class TeamAccessRequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "requester_user_id", nullable = false, updatable = false)
    private UUID requesterUserId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private AccessRequestStatus status;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeamAccessRequestEntity() {
        // JPA
    }

    public TeamAccessRequestEntity(UUID id, UUID teamId, UUID requesterUserId, UUID orgId) {
        this.id = id;
        this.teamId = teamId;
        this.requesterUserId = requesterUserId;
        this.orgId = orgId;
        this.status = AccessRequestStatus.PENDING;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getRequesterUserId() {
        return requesterUserId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public AccessRequestStatus getStatus() {
        return status;
    }

    public UUID getDecidedByUserId() {
        return decidedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Setters ──────────────────────────────────────────────

    public void decide(AccessRequestStatus decision, UUID decidedBy) {
        this.status = decision;
        this.decidedByUserId = decidedBy;
        this.decidedAt = Instant.now();
    }
}
