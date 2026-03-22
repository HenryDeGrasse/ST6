package com.weekly.team.repository;

import com.weekly.team.domain.TeamEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TeamEntity}. */
public interface TeamRepository extends JpaRepository<TeamEntity, UUID> {

    Optional<TeamEntity> findByOrgIdAndId(UUID orgId, UUID id);

    List<TeamEntity> findAllByOrgId(UUID orgId);

    List<TeamEntity> findAllByOrgIdAndOwnerUserId(UUID orgId, UUID ownerUserId);
}
