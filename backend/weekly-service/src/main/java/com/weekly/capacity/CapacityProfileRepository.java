package com.weekly.capacity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link CapacityProfileEntity}.
 */
@Repository
public interface CapacityProfileRepository
        extends JpaRepository<CapacityProfileEntity, CapacityProfileId> {

    /**
     * Returns the capacity profile for the given organisation and user.
     *
     * @param orgId  the organisation ID
     * @param userId the user ID
     * @return optional capacity profile
     */
    Optional<CapacityProfileEntity> findByOrgIdAndUserId(UUID orgId, UUID userId);

    /**
     * Returns all capacity profiles for the given organisation.
     *
     * @param orgId the organisation ID
     * @return list of capacity profiles, or empty if none found
     */
    List<CapacityProfileEntity> findByOrgId(UUID orgId);
}
