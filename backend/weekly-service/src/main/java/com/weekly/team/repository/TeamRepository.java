package com.weekly.team.repository;

import com.weekly.team.domain.TeamEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TeamEntity}. */
public interface TeamRepository extends JpaRepository<TeamEntity, UUID> {

    List<TeamEntity> findAllByOrgId(UUID orgId);
}
