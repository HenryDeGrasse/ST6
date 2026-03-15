package com.weekly.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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
}
