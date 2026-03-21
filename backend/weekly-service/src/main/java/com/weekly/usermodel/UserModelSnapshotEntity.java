package com.weekly.usermodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code user_model_snapshots} table.
 *
 * <p>Stores the most recent computed user model snapshot for a given
 * {@code (org_id, user_id)} pair. The snapshot is recomputed periodically
 * by {@code UserModelComputeJob} and exposed via the user profile API.
 *
 * <p>The composite primary key {@code (org_id, user_id)} is declared via
 * {@link IdClass} with {@link UserModelSnapshotId}.
 */
@Entity
@Table(name = "user_model_snapshots")
@IdClass(UserModelSnapshotId.class)
public class UserModelSnapshotEntity {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "weeks_analyzed", nullable = false)
    private int weeksAnalyzed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_json", nullable = false, columnDefinition = "jsonb")
    private String modelJson;

    /** No-arg constructor required by JPA. */
    protected UserModelSnapshotEntity() {
    }

    /**
     * Creates a new snapshot entity with {@code computedAt} set to now.
     *
     * @param orgId         the organisation this snapshot belongs to
     * @param userId        the user this snapshot belongs to
     * @param weeksAnalyzed the number of weeks used to derive the model
     * @param modelJson     the serialised model JSON to store in the JSONB column
     */
    public UserModelSnapshotEntity(UUID orgId, UUID userId, int weeksAnalyzed, String modelJson) {
        this.orgId = orgId;
        this.userId = userId;
        this.weeksAnalyzed = weeksAnalyzed;
        this.modelJson = modelJson;
        this.computedAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public int getWeeksAnalyzed() {
        return weeksAnalyzed;
    }

    public String getModelJson() {
        return modelJson;
    }

    // ── Setters (mutable fields only) ────────────────────────

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }

    public void setWeeksAnalyzed(int weeksAnalyzed) {
        this.weeksAnalyzed = weeksAnalyzed;
    }

    public void setModelJson(String modelJson) {
        this.modelJson = modelJson;
    }
}
