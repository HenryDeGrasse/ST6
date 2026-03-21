package com.weekly.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link IdempotencyKeyEntity}.
 *
 * <p>Queried by the {@link IdempotencyKeyFilter} to detect replay attempts
 * and retrieve cached responses for previously processed lifecycle mutations.
 */
@Repository
public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyEntity.IdempotencyKeyPK> {

    Optional<IdempotencyKeyEntity> findByOrgIdAndIdempotencyKey(UUID orgId, UUID idempotencyKey);

    /**
     * Deletes all idempotency keys whose {@code created_at} is before the given cutoff.
     *
     * @param cutoff keys older than this instant are deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKeyEntity k WHERE k.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

    // ── GDPR data deletion (PRD §14.7) ───────────────────────────────────────

    /**
     * Deletes all idempotency keys for the given user within the organisation.
     * Used by the GDPR right-to-be-forgotten process.
     *
     * @param orgId  the organisation ID
     * @param userId the user whose idempotency keys should be deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKeyEntity k WHERE k.orgId = :orgId AND k.userId = :userId")
    int deleteByOrgIdAndUserId(@Param("orgId") UUID orgId, @Param("userId") UUID userId);
}
