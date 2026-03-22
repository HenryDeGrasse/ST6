package com.weekly.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for the {@code team_members} table (Phase 6).
 *
 * <p>Composite PK: {@code (team_id, user_id)}.
 */
@Entity
@Table(name = "team_members")
@IdClass(TeamMemberEntity.TeamMemberId.class)
public class TeamMemberEntity {

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TeamRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected TeamMemberEntity() {
        // JPA
    }

    public TeamMemberEntity(UUID teamId, UUID userId, UUID orgId, TeamRole role) {
        this.teamId = teamId;
        this.userId = userId;
        this.orgId = orgId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public TeamRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    // ── Setters ──────────────────────────────────────────────

    public void setRole(TeamRole role) {
        this.role = role;
    }

    // ── Composite PK class ───────────────────────────────────

    /** Serialisable composite key for {@link TeamMemberEntity}. */
    public static class TeamMemberId implements Serializable {

        private UUID teamId;
        private UUID userId;

        public TeamMemberId() {
            // JPA
        }

        public TeamMemberId(UUID teamId, UUID userId) {
            this.teamId = teamId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TeamMemberId)) {
                return false;
            }
            TeamMemberId that = (TeamMemberId) o;
            return Objects.equals(teamId, that.teamId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(teamId, userId);
        }
    }
}
