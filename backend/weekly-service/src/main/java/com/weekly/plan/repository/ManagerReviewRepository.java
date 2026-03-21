package com.weekly.plan.repository;

import com.weekly.plan.domain.ManagerReviewEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ManagerReviewEntity}.
 */
@Repository
public interface ManagerReviewRepository extends JpaRepository<ManagerReviewEntity, UUID> {

    List<ManagerReviewEntity> findByOrgIdAndWeeklyPlanId(UUID orgId, UUID weeklyPlanId);

    /**
     * Finds all reviews for a set of plan IDs within the same org.
     * Used for batch look-up when computing review-turnaround statistics
     * across multiple plans without issuing N individual queries.
     */
    List<ManagerReviewEntity> findByOrgIdAndWeeklyPlanIdIn(UUID orgId, List<UUID> weeklyPlanIds);

    /**
     * Returns the distinct set of reviewer user IDs (managers) that have submitted
     * at least one review in the given organisation.
     *
     * <p>Used by the weekly digest job as an MVP approximation for "all managers in
     * the org who are active in the planning system". Managers who have never reviewed
     * any plan will not receive digests; this is an acceptable limitation for the
     * initial release.
     *
     * @param orgId the organisation ID
     * @return distinct manager user IDs
     */
    @Query("SELECT DISTINCT r.reviewerUserId FROM ManagerReviewEntity r WHERE r.orgId = :orgId")
    List<UUID> findDistinctReviewerUserIdsByOrgId(@Param("orgId") UUID orgId);
}
