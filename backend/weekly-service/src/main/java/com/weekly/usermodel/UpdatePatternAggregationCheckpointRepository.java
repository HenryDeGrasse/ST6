package com.weekly.usermodel;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisted update-pattern aggregation checkpoints.
 */
@Repository
public interface UpdatePatternAggregationCheckpointRepository
        extends JpaRepository<UpdatePatternAggregationCheckpointEntity, UUID> {

    Optional<UpdatePatternAggregationCheckpointEntity> findByOrgId(UUID orgId);
}
