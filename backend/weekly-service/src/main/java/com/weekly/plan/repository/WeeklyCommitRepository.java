package com.weekly.plan.repository;

import com.weekly.plan.domain.WeeklyCommitEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link WeeklyCommitEntity}.
 */
@Repository
public interface WeeklyCommitRepository extends JpaRepository<WeeklyCommitEntity, UUID> {

    List<WeeklyCommitEntity> findByOrgIdAndWeeklyPlanId(UUID orgId, UUID weeklyPlanId);

    int countByOrgIdAndWeeklyPlanId(UUID orgId, UUID weeklyPlanId);

    /**
     * Finds all commits for a set of plan IDs within the same org.
     * Used by the manager dashboard for team roll-up queries.
     */
    List<WeeklyCommitEntity> findByOrgIdAndWeeklyPlanIdIn(UUID orgId, List<UUID> weeklyPlanIds);

    /**
     * Soft-deletes all commits that have not yet been soft-deleted and whose
     * {@code created_at} is older than the given cutoff.
     *
     * <p>This is required in addition to plan soft-deletion because commits are
     * also queried directly by ID. Marking only the parent plan as deleted would
     * leave those commit lookups visible during the 90-day grace period.
     *
     * @param cutoff soft-delete commits created before this instant
     * @return the number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE weekly_commits SET deleted_at = NOW() "
            + "WHERE deleted_at IS NULL AND created_at < :cutoff",
            nativeQuery = true)
    int softDeleteCommitsBefore(@Param("cutoff") Instant cutoff);

    // ── GDPR data deletion (PRD §14.7) ───────────────────────────────────────

    /**
     * Soft-deletes all commits belonging to plans owned by the given user within
     * the organisation. Used by the GDPR right-to-be-forgotten process.
     *
     * <p>The subquery selects plan IDs for the owner regardless of their
     * soft-delete state, ensuring commits are hidden even if the parent plan
     * was just soft-deleted in the same transaction.
     *
     * @param orgId  the organisation ID
     * @param userId the user whose commits should be soft-deleted
     * @return the number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE weekly_commits SET deleted_at = NOW() "
            + "WHERE deleted_at IS NULL AND org_id = :orgId "
            + "AND weekly_plan_id IN "
            + "(SELECT id FROM weekly_plans WHERE org_id = :orgId AND owner_user_id = :userId)",
            nativeQuery = true)
    int softDeleteCommitsByUser(@Param("orgId") UUID orgId, @Param("userId") UUID userId);
}
