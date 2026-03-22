package com.weekly.team.repository;

import com.weekly.team.domain.TeamMemberEntity;
import com.weekly.team.domain.TeamMemberEntity.TeamMemberId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TeamMemberEntity} (Phase 6). */
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, TeamMemberId> {

    List<TeamMemberEntity> findAllByTeamId(UUID teamId);

    List<TeamMemberEntity> findAllByOrgIdAndUserId(UUID orgId, UUID userId);

    Optional<TeamMemberEntity> findByTeamIdAndUserId(UUID teamId, UUID userId);

    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    boolean existsByTeamIdAndUserIdIn(UUID teamId, Collection<UUID> userIds);
}
