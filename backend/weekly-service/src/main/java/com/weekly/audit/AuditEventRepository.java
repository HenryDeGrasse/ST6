package com.weekly.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AuditEventEntity} (append-only).
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findByOrgIdAndAggregateTypeAndAggregateId(
            UUID orgId, String aggregateType, UUID aggregateId);

    /**
     * Returns all audit events for the given aggregate, ordered chronologically.
     * Used by the audit state reconciliation job to reconstruct the expected state
     * history for a single plan.
     *
     * @param orgId         the organisation ID
     * @param aggregateType the aggregate type (e.g. "WeeklyPlan")
     * @param aggregateId   the aggregate ID (e.g. a plan UUID)
     * @return audit events ordered by {@code created_at} ascending
     */
    List<AuditEventEntity> findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            UUID orgId, String aggregateType, UUID aggregateId);

    /**
     * Returns the most recent audit event for the given organisation,
     * used to retrieve the previous row's hash when chaining (§14.7).
     *
     * @param orgId the organisation ID
     * @return the latest event, or empty if none exists yet
     */
    Optional<AuditEventEntity> findTopByOrgIdOrderByCreatedAtDesc(UUID orgId);

    /**
     * Returns all audit events for the given organisation ordered by
     * {@code created_at} ascending, paginated for memory-safe iteration
     * during hash chain verification.
     *
     * @param orgId    the organisation ID
     * @param pageable pagination parameters
     * @return a page of audit events in chronological order
     */
    Page<AuditEventEntity> findByOrgIdOrderByCreatedAtAsc(UUID orgId, Pageable pageable);

    /**
     * Returns the archival-eligible audit events for the given organisation,
     * ordered by {@code created_at} ascending and filtered in the database by
     * the age cutoff.
     *
     * @param orgId    the organisation ID
     * @param cutoff   only events created before this instant are returned
     * @param pageable pagination parameters
     * @return a page of archivable events in chronological order
     */
    Page<AuditEventEntity> findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
            UUID orgId, Instant cutoff, Pageable pageable);

    /**
     * Returns the distinct set of organisation IDs that have at least one
     * audit event. Used by the hash chain verification job to iterate all
     * organisations without loading all rows.
     *
     * @return list of distinct org IDs
     */
    @Query("SELECT DISTINCT e.orgId FROM AuditEventEntity e")
    List<UUID> findDistinctOrgIds();

    // ── GDPR data deletion (PRD §14.7) ───────────────────────────────────────

    /**
     * Replaces the {@code actor_user_id} of all audit events for the given user
     * within the organisation with a deterministic anonymised UUID.
     *
     * <p>Audit rows are never deleted; they are anonymised and then the stored
     * hash chain is recomputed so the user's identity is no longer recoverable
     * without creating a false-positive integrity break.
     *
     * <p>The {@code anonymisedId} is computed as
     * {@code SHA-256(userId.toString())} with the first 16 bytes re-interpreted
     * as a {@link java.util.UUID} (big-endian). This is deterministic: repeated
     * invocations for the same user produce the same anonymised ID.
     *
     * @param orgId        the organisation ID
     * @param userId       the original actor user ID to replace
     * @param anonymisedId the deterministic anonymised replacement UUID
     * @return the number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE audit_events SET actor_user_id = :anonymisedId "
            + "WHERE org_id = :orgId AND actor_user_id = :userId",
            nativeQuery = true)
    int anonymizeActorUserId(
            @Param("orgId") UUID orgId,
            @Param("userId") UUID userId,
            @Param("anonymisedId") UUID anonymisedId);

    /**
     * Returns all audit events for the given organisation ordered chronologically.
     *
     * <p>Used by GDPR anonymisation to recompute the tamper-detection hash chain
     * after sanctioned in-place anonymisation of {@code actor_user_id}.
     *
     * @param orgId the organisation ID
     * @return audit events ordered by {@code created_at} ascending
     */
    List<AuditEventEntity> findAllByOrgIdOrderByCreatedAtAsc(UUID orgId);

    /**
     * Rewrites the stored hash of a single audit event.
     *
     * <p>This is only used during GDPR anonymisation, where the payload of one or
     * more historical audit rows changes and the chain must be rehashed to remain
     * internally consistent.
     *
     * @param eventId the event to update
     * @param hash    the recomputed chained hash
     * @return number of rows updated
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE audit_events SET hash = :hash WHERE id = :eventId", nativeQuery = true)
    int updateHashById(@Param("eventId") UUID eventId, @Param("hash") String hash);
}
