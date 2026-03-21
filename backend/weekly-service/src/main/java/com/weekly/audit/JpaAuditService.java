package com.weekly.audit;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * JPA-backed implementation of {@link AuditService}.
 *
 * <p>Persists audit events as append-only rows in the {@code audit_events} table.
 * Called within the same transaction as the domain write, ensuring atomicity.
 *
 * <p>Before saving, the service fetches the most recent event for the same
 * organisation, then computes
 * {@code SHA-256(previousHash + payload)} via
 * {@link AuditEventEntity#computeHash(String, String)}, forming a
 * tamper-detection hash chain (§14.7).
 */
@Service
public class JpaAuditService implements AuditService {

    private final AuditEventRepository auditEventRepository;

    public JpaAuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void record(
            UUID orgId,
            UUID actorUserId,
            String action,
            String aggregateType,
            UUID aggregateId,
            String previousState,
            String newState,
            String reason,
            String ipAddress,
            String correlationId
    ) {
        AuditEventEntity event = new AuditEventEntity(
                orgId, actorUserId, action,
                aggregateType, aggregateId,
                previousState, newState,
                reason, ipAddress, correlationId
        );

        Optional<AuditEventEntity> previousEvent =
                auditEventRepository.findTopByOrgIdOrderByCreatedAtDesc(orgId);
        String previousHash = previousEvent
                .map(AuditEventEntity::getHash)
                .orElse("");

        String payload = event.buildPayload();
        event.setHash(AuditEventEntity.computeHash(previousHash, payload));

        auditEventRepository.save(event);
    }
}
