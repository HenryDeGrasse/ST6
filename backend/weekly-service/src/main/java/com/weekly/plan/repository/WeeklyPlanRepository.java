package com.weekly.plan.repository;

import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyPlanEntity;
import java.time.Instant;
import java.time.LocalDate;
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
 * Spring Data JPA repository for {@link WeeklyPlanEntity}.
 */
@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlanEntity, UUID> {

    Optional<WeeklyPlanEntity> findByOrgIdAndOwnerUserIdAndWeekStartDate(
            UUID orgId, UUID ownerUserId, LocalDate weekStartDate);

    Optional<WeeklyPlanEntity> findByOrgIdAndId(UUID orgId, UUID id);

    /**
     * Finds all plans for a set of users in a given week within the same org.
     * Used by the manager dashboard to fetch direct reports' plans.
     */
    List<WeeklyPlanEntity> findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
            UUID orgId, LocalDate weekStartDate, List<UUID> ownerUserIds);

    /**
     * Finds plans in a specific state for a given org and week.
     * Used by cadence reminder job to detect stale plans.
     *
     * @param orgId         the organisation
     * @param state         the plan state to filter by
     * @param weekStartDate the Monday of the target week
     * @return matching plans
     */
    List<WeeklyPlanEntity> findByOrgIdAndStateAndWeekStartDate(
            UUID orgId, PlanState state, LocalDate weekStartDate);

    /**
     * Finds plans whose state is in the given list and whose week started before the given date.
     * Used by cadence reminder job to find overdue plans.
     *
     * @param orgId         the organisation
     * @param states        the set of plan states to match
     * @param weekStartDate plans must have weekStartDate strictly before this date
     * @return matching plans
     */
    List<WeeklyPlanEntity> findByOrgIdAndStateInAndWeekStartDateBefore(
            UUID orgId, List<PlanState> states, LocalDate weekStartDate);

    /**
     * Returns the distinct set of organisation IDs that have at least one weekly plan.
     * Used by the cadence reminder job so orgs without an explicit org_policies row
     * still receive reminders via {@link com.weekly.config.OrgPolicyService#defaultPolicy()}.
     *
     * @return distinct org IDs with plans
     */
    @Query("SELECT DISTINCT p.orgId FROM WeeklyPlanEntity p")
    List<UUID> findDistinctOrgIds();

    /**
     * Returns all plans for the given organisation, paginated.
     * Used by the audit state reconciliation job to iterate plans without loading
     * the entire table into memory.
     *
     * @param orgId    the organisation ID
     * @param pageable pagination parameters
     * @return a page of plans belonging to the organisation
     */
    Page<WeeklyPlanEntity> findByOrgId(UUID orgId, Pageable pageable);

    /**
     * Returns plans for a specific user within a date range (inclusive).
     * Used by {@link com.weekly.trends.TrendsService} for rolling-window aggregations.
     *
     * @param orgId         the organisation ID
     * @param ownerUserId   the user ID
     * @param startDate     start of the rolling window (inclusive)
     * @param endDate       end of the rolling window (inclusive)
     * @return matching plans sorted by weekStartDate ascending
     */
    List<WeeklyPlanEntity> findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
            UUID orgId, UUID ownerUserId, LocalDate startDate, LocalDate endDate);

    /**
     * Returns all plans across all users within a date range (inclusive).
     * Used by {@link com.weekly.trends.TrendsService} to compute org-wide team averages.
     *
     * @param orgId     the organisation ID
     * @param startDate start of the rolling window (inclusive)
     * @param endDate   end of the rolling window (inclusive)
     * @return matching plans
     */
    List<WeeklyPlanEntity> findByOrgIdAndWeekStartDateBetween(
            UUID orgId, LocalDate startDate, LocalDate endDate);

    // ── Retention queries (PRD §14.7) ────────────────────────────────────────

    /**
     * Soft-deletes all plans that have not yet been soft-deleted and whose
     * {@code created_at} is older than the given cutoff.
     *
     * <p>Note: the {@code @SQLRestriction("deleted_at IS NULL")} on
     * {@link com.weekly.plan.domain.WeeklyPlanEntity} is bypassed by this
     * native JPQL {@code UPDATE} which targets all rows regardless of filter.
     *
     * @param cutoff soft-delete plans created before this instant
     * @return the number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE weekly_plans SET deleted_at = NOW() "
            + "WHERE deleted_at IS NULL AND created_at < :cutoff",
            nativeQuery = true)
    int softDeletePlansBefore(@Param("cutoff") Instant cutoff);

    /**
     * Hard-deletes all plans whose soft-delete timestamp is older than the
     * given grace-period cutoff. Commits and actuals are removed via
     * {@code ON DELETE CASCADE}.
     *
     * @param graceCutoff hard-delete plans soft-deleted before this instant
     * @return the number of rows deleted
     */
    @Modifying
    @Query(value = "DELETE FROM weekly_plans "
            + "WHERE deleted_at IS NOT NULL AND deleted_at < :graceCutoff",
            nativeQuery = true)
    int hardDeleteSoftDeletedPlansBefore(@Param("graceCutoff") Instant graceCutoff);

    // ── GDPR data deletion (PRD §14.7) ────────────────────────────────────────

    /**
     * Soft-deletes all plans owned by the given user within the organisation that
     * have not yet been soft-deleted. Used by the GDPR right-to-be-forgotten process.
     *
     * @param orgId  the organisation ID
     * @param userId the user whose plans should be soft-deleted
     * @return the number of rows updated
     */
    @Modifying
    @Query(value = "UPDATE weekly_plans SET deleted_at = NOW() "
            + "WHERE org_id = :orgId AND owner_user_id = :userId AND deleted_at IS NULL",
            nativeQuery = true)
    int softDeletePlansByUser(@Param("orgId") UUID orgId, @Param("userId") UUID userId);
}
