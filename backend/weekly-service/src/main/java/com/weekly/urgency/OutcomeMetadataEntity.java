package com.weekly.urgency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code outcome_metadata} table.
 *
 * <p>Stores target-date, progress tracking configuration, and computed urgency
 * band for each RCDO outcome. One row per {@code (org_id, outcome_id)}.
 *
 * <p>The {@code progress_pct} and {@code urgency_band} fields are recomputed
 * on a schedule by {@code UrgencyComputeJob}.
 */
@Entity
@Table(name = "outcome_metadata")
@IdClass(OutcomeMetadataId.class)
public class OutcomeMetadataEntity {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Id
    @Column(name = "outcome_id", nullable = false, updatable = false)
    private UUID outcomeId;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "progress_type", nullable = false, length = 20)
    private String progressType;

    @Column(name = "metric_name", length = 200)
    private String metricName;

    @Column(name = "target_value")
    private BigDecimal targetValue;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "milestones", columnDefinition = "jsonb")
    private String milestones;

    @Column(name = "progress_pct", precision = 5, scale = 2)
    private BigDecimal progressPct;

    @Column(name = "urgency_band", length = 20)
    private String urgencyBand;

    @Column(name = "last_computed_at")
    private Instant lastComputedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutcomeMetadataEntity() {
        // JPA
    }

    public OutcomeMetadataEntity(UUID orgId, UUID outcomeId) {
        this.orgId = orgId;
        this.outcomeId = outcomeId;
        this.progressType = "ACTIVITY";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getOutcomeId() {
        return outcomeId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getProgressType() {
        return progressType;
    }

    public String getMetricName() {
        return metricName;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public String getUnit() {
        return unit;
    }

    public String getMilestones() {
        return milestones;
    }

    public BigDecimal getProgressPct() {
        return progressPct;
    }

    public String getUrgencyBand() {
        return urgencyBand;
    }

    public Instant getLastComputedAt() {
        return lastComputedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ── Setters ──────────────────────────────────────────────

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
        this.updatedAt = Instant.now();
    }

    public void setProgressType(String progressType) {
        this.progressType = progressType;
        this.updatedAt = Instant.now();
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
        this.updatedAt = Instant.now();
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
        this.updatedAt = Instant.now();
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
        this.updatedAt = Instant.now();
    }

    public void setUnit(String unit) {
        this.unit = unit;
        this.updatedAt = Instant.now();
    }

    public void setMilestones(String milestones) {
        this.milestones = milestones;
        this.updatedAt = Instant.now();
    }

    public void setProgressPct(BigDecimal progressPct) {
        this.progressPct = progressPct;
        this.updatedAt = Instant.now();
    }

    public void setUrgencyBand(String urgencyBand) {
        this.urgencyBand = urgencyBand;
        this.updatedAt = Instant.now();
    }

    public void setLastComputedAt(Instant lastComputedAt) {
        this.lastComputedAt = lastComputedAt;
        this.updatedAt = Instant.now();
    }
}
