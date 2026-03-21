package com.weekly.usermodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores the last successfully aggregated progress-entry timestamp for an org.
 *
 * <p>This checkpoint lets {@link UpdatePatternAggregationJob} resume safely
 * across worker restarts without re-aggregating the same lookback window.
 */
@Entity
@Table(name = "update_pattern_aggregation_checkpoints")
public class UpdatePatternAggregationCheckpointEntity {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "last_aggregated_at", nullable = false)
    private Instant lastAggregatedAt;

    protected UpdatePatternAggregationCheckpointEntity() {
        // JPA
    }

    public UpdatePatternAggregationCheckpointEntity(UUID orgId, Instant lastAggregatedAt) {
        this.orgId = orgId;
        this.lastAggregatedAt = lastAggregatedAt;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public Instant getLastAggregatedAt() {
        return lastAggregatedAt;
    }

    public void setLastAggregatedAt(Instant lastAggregatedAt) {
        this.lastAggregatedAt = lastAggregatedAt;
    }
}
