package com.weekly.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for the {@code idempotency_keys} table.
 *
 * <p>Stores the cached response for lifecycle mutation requests so that
 * network retries replay the original result rather than re-executing
 * the state transition.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyEntity.IdempotencyKeyPK.class)
public class IdempotencyKeyEntity {

    /**
     * Composite primary key class for (org_id, idempotency_key).
     */
    public static class IdempotencyKeyPK implements Serializable {

        private UUID orgId;
        private UUID idempotencyKey;

        protected IdempotencyKeyPK() {
            // JPA
        }

        public IdempotencyKeyPK(UUID orgId, UUID idempotencyKey) {
            this.orgId = orgId;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdempotencyKeyPK that)) {
                return false;
            }
            return Objects.equals(orgId, that.orgId)
                    && Objects.equals(idempotencyKey, that.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orgId, idempotencyKey);
        }
    }

    @Id
    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, updatable = false, length = 200)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false, updatable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false, updatable = false)
    private Map<String, Object> responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
        // JPA
    }

    public IdempotencyKeyEntity(
            UUID orgId,
            UUID idempotencyKey,
            UUID userId,
            String endpoint,
            String requestHash,
            int responseStatus,
            Map<String, Object> responseBody
    ) {
        this.orgId = orgId;
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    // ── Getters ──────────────────────────────────────────────

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public Map<String, Object> getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
