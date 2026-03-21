package com.weekly.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of {@link IdempotencyKeyService}.
 *
 * <p>Separated from the filter so that DB operations can participate in
 * Spring-managed transactions while the servlet filter itself runs outside
 * the Spring transaction boundary.
 */
@Service
public class JpaIdempotencyKeyService implements IdempotencyKeyService {

    private static final Logger LOG = LoggerFactory.getLogger(JpaIdempotencyKeyService.class);

    private final IdempotencyKeyRepository repository;

    public JpaIdempotencyKeyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyKeyEntity> findExisting(UUID orgId, UUID idempotencyKey) {
        return repository.findByOrgIdAndIdempotencyKey(orgId, idempotencyKey);
    }

    @Override
    @Transactional
    public void store(
            UUID orgId,
            UUID idempotencyKey,
            UUID userId,
            String endpoint,
            String requestHash,
            int responseStatus,
            Map<String, Object> responseBody
    ) {
        try {
            IdempotencyKeyEntity entity = new IdempotencyKeyEntity(
                    orgId, idempotencyKey, userId, endpoint, requestHash,
                    responseStatus, responseBody
            );
            repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request with the same key already stored a response;
            // the DB unique constraint guarantees at-most-one entry.
            LOG.warn(
                "Idempotency key {} already stored for org {} (concurrent write)",
                idempotencyKey, orgId
            );
        }
    }
}
