package com.weekly.urgency;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OutcomeMetadataEntity}.
 */
@Repository
public interface OutcomeMetadataRepository
        extends JpaRepository<OutcomeMetadataEntity, OutcomeMetadataId> {

    /**
     * Returns all outcome metadata rows for the given organisation.
     *
     * @param orgId the organisation ID
     * @return list of outcome metadata, or empty if none found
     */
    List<OutcomeMetadataEntity> findByOrgId(UUID orgId);

    /**
     * Returns the outcome metadata for the given organisation and outcome.
     *
     * @param orgId     the organisation ID
     * @param outcomeId the outcome ID
     * @return optional outcome metadata
     */
    Optional<OutcomeMetadataEntity> findByOrgIdAndOutcomeId(UUID orgId, UUID outcomeId);

    /**
     * Returns all outcome metadata for the given organisation filtered by urgency band.
     *
     * @param orgId       the organisation ID
     * @param urgencyBand the urgency band to filter by (e.g. "AT_RISK", "CRITICAL")
     * @return list of matching outcome metadata, or empty if none found
     */
    List<OutcomeMetadataEntity> findByOrgIdAndUrgencyBand(UUID orgId, String urgencyBand);

    /**
     * Returns the distinct set of organisation IDs that have at least one outcome
     * metadata row. Used by {@link UrgencyComputeJob} to iterate all organisations
     * without loading all rows.
     *
     * @return list of distinct org IDs
     */
    @Query("SELECT DISTINCT m.orgId FROM OutcomeMetadataEntity m")
    List<UUID> findDistinctOrgIds();
}
