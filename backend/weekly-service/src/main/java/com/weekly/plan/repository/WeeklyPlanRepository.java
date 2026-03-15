package com.weekly.plan.repository;

import com.weekly.plan.domain.WeeklyPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
