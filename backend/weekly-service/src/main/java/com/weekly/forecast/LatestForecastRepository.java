package com.weekly.forecast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisted latest forecasts.
 */
@Repository
public interface LatestForecastRepository extends JpaRepository<LatestForecastEntity, LatestForecastId> {

    Optional<LatestForecastEntity> findByOrgIdAndOutcomeId(UUID orgId, UUID outcomeId);

    List<LatestForecastEntity> findByOrgId(UUID orgId);

    void deleteByOrgIdAndOutcomeId(UUID orgId, UUID outcomeId);

    void deleteByOrgId(UUID orgId);
}
