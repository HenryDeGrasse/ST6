package com.weekly.plan.repository;

import com.weekly.plan.domain.ManagerReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ManagerReviewEntity}.
 */
@Repository
public interface ManagerReviewRepository extends JpaRepository<ManagerReviewEntity, UUID> {

    List<ManagerReviewEntity> findByOrgIdAndWeeklyPlanId(UUID orgId, UUID weeklyPlanId);
}
