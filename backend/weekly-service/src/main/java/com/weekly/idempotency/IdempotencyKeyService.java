package com.weekly.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract for idempotency key persistence.
 *
 * <p>Implementations must ensure {@link #findExisting} and {@link #store}
 * execute within appropriate database transactions.
 */
public interface IdempotencyKeyService {

    /**
     * Looks up an existing cached response for the given (orgId, idempotencyKey) pair.
     */
    Optional<IdempotencyKeyEntity> findExisting(UUID orgId, UUID idempotencyKey);

    /**
     * Stores a cached response for future replay.
     *
     * <p>Implementations must handle duplicate-key races gracefully (first writer wins).
     */
    void store(
            UUID orgId,
            UUID idempotencyKey,
            UUID userId,
            String endpoint,
            String requestHash,
            int responseStatus,
            Map<String, Object> responseBody
    );
}
