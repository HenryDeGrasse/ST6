package com.weekly.assignment.repository;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link WeeklyAssignmentEntity} (Phase 6). */
public interface WeeklyAssignmentRepository extends JpaRepository<WeeklyAssignmentEntity, UUID> {

    List<WeeklyAssignmentEntity> findAllByOrgIdAndWeeklyPlanId(UUID orgId, UUID weeklyPlanId);

    Optional<WeeklyAssignmentEntity> findByWeeklyPlanIdAndIssueId(UUID weeklyPlanId, UUID issueId);

    Optional<WeeklyAssignmentEntity> findByLegacyCommitId(UUID legacyCommitId);

    Optional<WeeklyAssignmentEntity> findByOrgIdAndId(UUID orgId, UUID id);

    List<WeeklyAssignmentEntity> findAllByIssueId(UUID issueId);
}
