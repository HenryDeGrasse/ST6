package com.weekly.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code org_policies} table (PRD §5).
 *
 * <p>Stores per-org configuration: chess priority rules and RCDO staleness thresholds.
 * Rows are created at org-provisioning time; the system falls back to hardcoded
 * defaults if no row exists for an org.
 */
@Entity
@Table(name = "org_policies")
public class OrgPolicyEntity {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "chess_king_required", nullable = false)
    private boolean chessKingRequired;

    @Column(name = "chess_max_king", nullable = false)
    private int chessMaxKing;

    @Column(name = "chess_max_queen", nullable = false)
    private int chessMaxQueen;

    @Column(name = "lock_day", nullable = false, length = 10)
    private String lockDay;

    @Column(name = "lock_time", nullable = false, length = 5)
    private String lockTime;

    @Column(name = "reconcile_day", nullable = false, length = 10)
    private String reconcileDay;

    @Column(name = "reconcile_time", nullable = false, length = 5)
    private String reconcileTime;

    @Column(name = "block_lock_on_stale_rcdo", nullable = false)
    private boolean blockLockOnStaleRcdo;

    @Column(name = "rcdo_staleness_threshold_minutes", nullable = false)
    private int rcdoStalenessThresholdMinutes;

    @Column(name = "digest_day", length = 10)
    private String digestDay;

    @Column(name = "digest_time", length = 5)
    private String digestTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrgPolicyEntity() {
        // JPA
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getOrgId() {
        return orgId;
    }

    public boolean isChessKingRequired() {
        return chessKingRequired;
    }

    public int getChessMaxKing() {
        return chessMaxKing;
    }

    public int getChessMaxQueen() {
        return chessMaxQueen;
    }

    public String getLockDay() {
        return lockDay;
    }

    public String getLockTime() {
        return lockTime;
    }

    public String getReconcileDay() {
        return reconcileDay;
    }

    public String getReconcileTime() {
        return reconcileTime;
    }

    public boolean isBlockLockOnStaleRcdo() {
        return blockLockOnStaleRcdo;
    }

    public int getRcdoStalenessThresholdMinutes() {
        return rcdoStalenessThresholdMinutes;
    }

    public String getDigestDay() {
        return digestDay;
    }

    public String getDigestTime() {
        return digestTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ── Mutation ─────────────────────────────────────────────

    /**
     * Updates the weekly digest schedule. Called by {@code CachingOrgPolicyService}
     * when an admin patches the digest config.
     *
     * @param digestDay  day-of-week string (e.g. "FRIDAY")
     * @param digestTime HH:mm time string (e.g. "17:00")
     */
    public void updateDigestConfig(String digestDay, String digestTime) {
        this.digestDay = digestDay.toUpperCase();
        this.digestTime = digestTime;
        this.updatedAt = Instant.now();
    }
}
