package com.weekly.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OutboxEventEntity}.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();

    /**
     * Deletes all outbox events whose {@code occurred_at} is before the given cutoff.
     *
     * @param cutoff events older than this instant are deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") Instant cutoff);
}
