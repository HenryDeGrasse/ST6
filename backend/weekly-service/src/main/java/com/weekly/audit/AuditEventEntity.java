package com.weekly.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * JPA entity for the {@code audit_events} table.
 *
 * <p>Append-only audit trail. Every state transition and every write
 * to a locked plan produces an audit event with actor, action, timestamp,
 * previous/new state, and reason.
 *
 * <p>Each row carries a {@code hash} field: SHA-256(previousHash + payload),
 * where payload is a deterministic JSON serialisation of the event's key fields.
 * This forms a tamper-detection hash chain (§14.7).
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

    @Column(name = "hash", updatable = false, length = 128)
    private String hash;

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

    /**
     * Computes SHA-256(previousHash + payload) and returns the lowercase hex string.
     *
     * @param previousHash the hash from the preceding event in the chain;
     *                     use an empty string for the very first event
     * @param payload      a deterministic serialisation of the event's key fields
     * @return 64-character lowercase hexadecimal SHA-256 digest
     */
    public static String computeHash(String previousHash, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (previousHash + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Builds the deterministic canonical JSON payload used for hash computation.
     *
     * <p>Keys are ordered alphabetically (via {@link TreeMap}) so that the same
     * event always produces the same payload string regardless of insertion order.
     * Nullable fields are serialised as JSON {@code null}.
     *
     * @return JSON string representation of the event's key fields
     */
    String buildPayload() {
        Map<String, String> fields = new TreeMap<>();
        fields.put("action", action);
        fields.put("actorUserId", actorUserId != null ? actorUserId.toString() : null);
        fields.put("aggregateId", aggregateId != null ? aggregateId.toString() : null);
        fields.put("aggregateType", aggregateType);
        fields.put("createdAt", createdAt != null ? createdAt.toString() : null);
        fields.put("newState", newState);
        fields.put("orgId", orgId != null ? orgId.toString() : null);
        fields.put("previousState", previousState);
        fields.put("reason", reason);

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(jsonEscape(entry.getValue())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Recomputes this event's chained hash using its current payload.
     *
     * <p>This is used by the GDPR anonymisation flow, which intentionally updates
     * {@code actor_user_id} while preserving audit-chain integrity by rewriting the
     * affected hashes inside the same transaction.
     *
     * @param previousHash the previous event's hash in the chain
     * @return the recomputed chained hash for this event
     */
    public String computeChainedHash(String previousHash) {
        return computeHash(previousHash, buildPayload());
    }

    private static String jsonEscape(String s) {
        StringBuilder escaped = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /** Sets the hash field. Package-private; called by {@link JpaAuditService}. */
    void setHash(String newHash) {
        this.hash = newHash;
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

    public String getHash() {
        return hash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
