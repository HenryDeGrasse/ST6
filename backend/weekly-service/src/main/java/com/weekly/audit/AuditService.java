package com.weekly.audit;

import java.util.UUID;

/**
 * Audit service interface for recording lifecycle events (§5, §14.7).
 *
 * <p>Every state transition and every write to a locked plan produces
 * an audit event row with actor, action, timestamp, previous/new state,
 * and reason. The audit table is append-only.
 */
public interface AuditService {

    /**
     * Records an audit event.
     *
     * @param orgId         the organization ID
     * @param actorUserId   who performed the action
     * @param action        the action name (e.g., "PLAN_LOCKED", "COMMIT_DELETED")
     * @param aggregateType the entity type (e.g., "WeeklyPlan", "WeeklyCommit")
     * @param aggregateId   the entity ID
     * @param previousState the state before the action (nullable)
     * @param newState      the state after the action (nullable)
     * @param reason        optional reason for the transition
     * @param ipAddress     the client IP address (nullable)
     * @param correlationId the request correlation ID (nullable)
     */
    void record(
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
    );
}
