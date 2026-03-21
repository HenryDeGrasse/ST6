package com.weekly.capacity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code user_capacity_profiles} table.
 *
 * <p>Stores the computed capacity profile for each user within an organisation.
 * Profiles are updated weekly by the {@code CapacityComputeJob} scheduled task.
 *
 * <p>The composite primary key {@code (org_id, user_id)} is handled via
 * {@link IdClass} following the same pattern as {@code OutcomeMetadataEntity}.
 */
@Entity
@Table(name = "user_capacity_profiles")
@IdClass(CapacityProfileId.class)
public class CapacityProfileEntity {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "weeks_analyzed", nullable = false)
    private int weeksAnalyzed;

    @Column(name = "avg_estimated_hours", precision = 5, scale = 1)
    private BigDecimal avgEstimatedHours;

    @Column(name = "avg_actual_hours", precision = 5, scale = 1)
    private BigDecimal avgActualHours;

    @Column(name = "estimation_bias", precision = 4, scale = 2)
    private BigDecimal estimationBias;

    @Column(name = "realistic_weekly_cap", precision = 5, scale = 1)
    private BigDecimal realisticWeeklyCap;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_bias_json", columnDefinition = "jsonb")
    private String categoryBiasJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "priority_completion_json", columnDefinition = "jsonb")
    private String priorityCompletionJson;

    @Column(name = "confidence_level", length = 10)
    private String confidenceLevel;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected CapacityProfileEntity() {
        // JPA
    }

    public CapacityProfileEntity(UUID orgId, UUID userId) {
        this.orgId = orgId;
        this.userId = userId;
        this.weeksAnalyzed = 0;
        this.computedAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getWeeksAnalyzed() {
        return weeksAnalyzed;
    }

    public BigDecimal getAvgEstimatedHours() {
        return avgEstimatedHours;
    }

    public BigDecimal getAvgActualHours() {
        return avgActualHours;
    }

    public BigDecimal getEstimationBias() {
        return estimationBias;
    }

    public BigDecimal getRealisticWeeklyCap() {
        return realisticWeeklyCap;
    }

    public String getCategoryBiasJson() {
        return categoryBiasJson;
    }

    public String getPriorityCompletionJson() {
        return priorityCompletionJson;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    // ── Setters (used by CapacityProfileService) ─────────────

    public void setWeeksAnalyzed(int weeksAnalyzed) {
        this.weeksAnalyzed = weeksAnalyzed;
    }

    public void setAvgEstimatedHours(BigDecimal avgEstimatedHours) {
        this.avgEstimatedHours = avgEstimatedHours;
    }

    public void setAvgActualHours(BigDecimal avgActualHours) {
        this.avgActualHours = avgActualHours;
    }

    public void setEstimationBias(BigDecimal estimationBias) {
        this.estimationBias = estimationBias;
    }

    public void setRealisticWeeklyCap(BigDecimal realisticWeeklyCap) {
        this.realisticWeeklyCap = realisticWeeklyCap;
    }

    public void setCategoryBiasJson(String categoryBiasJson) {
        this.categoryBiasJson = categoryBiasJson;
    }

    public void setPriorityCompletionJson(String priorityCompletionJson) {
        this.priorityCompletionJson = priorityCompletionJson;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
