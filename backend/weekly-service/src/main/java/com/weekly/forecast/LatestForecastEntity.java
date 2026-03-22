package com.weekly.forecast;

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
 * Persisted latest calibrated target-date forecast per outcome.
 *
 * <p>This read model is shared by the daily forecasting job, outcome-facing APIs,
 * and future executive rollups so forecasting does not need to be recomputed on
 * every request.
 */
@Entity
@Table(name = "latest_forecasts")
@IdClass(LatestForecastId.class)
public class LatestForecastEntity {

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Id
    @Column(name = "outcome_id", nullable = false, updatable = false)
    private UUID outcomeId;

    @Column(name = "projected_target_date")
    private LocalDate projectedTargetDate;

    @Column(name = "projected_progress_pct", precision = 5, scale = 2)
    private BigDecimal projectedProgressPct;

    @Column(name = "projected_velocity", precision = 8, scale = 4)
    private BigDecimal projectedVelocity;

    @Column(name = "confidence_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal confidenceScore;

    @Column(name = "forecast_status", nullable = false, length = 32)
    private String forecastStatus;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forecast_inputs_json", nullable = false, columnDefinition = "jsonb")
    private String forecastInputsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forecast_details_json", nullable = false, columnDefinition = "jsonb")
    private String forecastDetailsJson;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LatestForecastEntity() {
        // JPA
    }

    public LatestForecastEntity(UUID orgId, UUID outcomeId) {
        this.orgId = orgId;
        this.outcomeId = outcomeId;
        this.confidenceScore = BigDecimal.ZERO;
        this.forecastStatus = "NO_DATA";
        this.modelVersion = "phase5-foundation";
        this.forecastInputsJson = "{}";
        this.forecastDetailsJson = "{}";
        Instant now = Instant.now();
        this.computedAt = now;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getOutcomeId() {
        return outcomeId;
    }

    public LocalDate getProjectedTargetDate() {
        return projectedTargetDate;
    }

    public void setProjectedTargetDate(LocalDate projectedTargetDate) {
        this.projectedTargetDate = projectedTargetDate;
        touch();
    }

    public BigDecimal getProjectedProgressPct() {
        return projectedProgressPct;
    }

    public void setProjectedProgressPct(BigDecimal projectedProgressPct) {
        this.projectedProgressPct = projectedProgressPct;
        touch();
    }

    public BigDecimal getProjectedVelocity() {
        return projectedVelocity;
    }

    public void setProjectedVelocity(BigDecimal projectedVelocity) {
        this.projectedVelocity = projectedVelocity;
        touch();
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
        touch();
    }

    public String getForecastStatus() {
        return forecastStatus;
    }

    public void setForecastStatus(String forecastStatus) {
        this.forecastStatus = forecastStatus;
        touch();
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
        touch();
    }

    public String getForecastInputsJson() {
        return forecastInputsJson;
    }

    public void setForecastInputsJson(String forecastInputsJson) {
        this.forecastInputsJson = forecastInputsJson;
        touch();
    }

    public String getForecastDetailsJson() {
        return forecastDetailsJson;
    }

    public void setForecastDetailsJson(String forecastDetailsJson) {
        this.forecastDetailsJson = forecastDetailsJson;
        touch();
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
        touch();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
