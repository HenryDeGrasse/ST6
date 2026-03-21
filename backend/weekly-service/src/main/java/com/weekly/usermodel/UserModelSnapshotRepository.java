package com.weekly.usermodel;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link UserModelSnapshotEntity}.
 *
 * <p>Provides look-up and upsert support for user model snapshots.
 * The composite primary key is managed via {@link UserModelSnapshotId}.
 */
@Repository
public interface UserModelSnapshotRepository
        extends JpaRepository<UserModelSnapshotEntity, UserModelSnapshotId> {

    /**
     * Looks up the snapshot for the given organisation and user.
     *
     * @param orgId  the organisation ID
     * @param userId the user ID
     * @return the snapshot, or empty if none has been computed yet
     */
    Optional<UserModelSnapshotEntity> findByOrgIdAndUserId(UUID orgId, UUID userId);
}
