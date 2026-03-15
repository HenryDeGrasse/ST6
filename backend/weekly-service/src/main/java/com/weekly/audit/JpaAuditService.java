package com.weekly.audit;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * JPA-backed implementation of {@link AuditService}.
 *
 * <p>Persists audit events as append-only rows in the {@code audit_events} table.
 * Called within the same transaction as the domain write, ensuring atomicity.
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
        auditEventRepository.save(event);
    }
}
