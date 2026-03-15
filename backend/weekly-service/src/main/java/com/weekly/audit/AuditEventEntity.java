package com.weekly.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code audit_events} table.
 *
 * <p>Append-only audit trail. Every state transition and every write
 * to a locked plan produces an audit event with actor, action, timestamp,
 * previous/new state, and reason.
 */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "previous_state", updatable = false, length = 50)
    private String previousState;

    @Column(name = "new_state", updatable = false, length = 50)
    private String newState;

    @Column(name = "reason", updatable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "correlation_id", updatable = false, length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
        // JPA
    }

    public AuditEventEntity(
            UUID orgId, UUID actorUserId, String action,
            String aggregateType, UUID aggregateId,
            String previousState, String newState,
            String reason, String ipAddress, String correlationId
    ) {
        this.id = UUID.randomUUID();
        this.orgId = orgId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.previousState = previousState;
        this.newState = newState;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public String getReason() {
        return reason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
