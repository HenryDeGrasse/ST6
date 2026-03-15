package com.weekly.plan.repository;

import com.weekly.plan.domain.WeeklyCommitActualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WeeklyCommitActualEntity}.
 */
@Repository
public interface WeeklyCommitActualRepository extends JpaRepository<WeeklyCommitActualEntity, UUID> {

    Optional<WeeklyCommitActualEntity> findByOrgIdAndCommitId(UUID orgId, UUID commitId);

    List<WeeklyCommitActualEntity> findByOrgIdAndCommitIdIn(UUID orgId, List<UUID> commitIds);
}
