package com.weekly.plan.repository;

import com.weekly.plan.domain.WeeklyCommitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
}
