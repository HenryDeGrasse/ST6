package com.weekly.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link NotificationEntity}.
 */
@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID orgId, UUID userId);

    List<NotificationEntity> findByOrgIdAndUserIdOrderByCreatedAtDesc(
            UUID orgId, UUID userId);

    /**
     * Returns notifications of the same type for the same user/org created since the cutoff,
     * ordered newest first. Used by cadence reminders to apply finer-grained idempotency
     * by plan/week scope.
     *
     * @param orgId   the organisation
     * @param userId  the recipient user
     * @param type    the notification type
     * @param cutoff  only notifications created on or after this instant are returned
     * @return matching notifications ordered newest first
     */
    List<NotificationEntity> findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            UUID orgId, UUID userId, String type, Instant cutoff);

    /**
     * Deletes all notifications whose {@code created_at} is before the given cutoff.
     *
     * @param cutoff notifications older than this instant are deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

    // ── GDPR data deletion (PRD §14.7) ───────────────────────────────────────

    /**
     * Deletes all notifications for the given user within the organisation.
     * Used by the GDPR right-to-be-forgotten process.
     *
     * @param orgId  the organisation ID
     * @param userId the user whose notifications should be deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.orgId = :orgId AND n.userId = :userId")
    int deleteByOrgIdAndUserId(@Param("orgId") UUID orgId, @Param("userId") UUID userId);
}
