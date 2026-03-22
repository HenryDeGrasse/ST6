package com.weekly.team.repository;

import com.weekly.team.domain.AccessRequestStatus;
import com.weekly.team.domain.TeamAccessRequestEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TeamAccessRequestEntity} (Phase 6). */
public interface TeamAccessRequestRepository extends JpaRepository<TeamAccessRequestEntity, UUID> {

    List<TeamAccessRequestEntity> findAllByTeamIdAndStatus(UUID teamId, AccessRequestStatus status);

    Optional<TeamAccessRequestEntity> findByTeamIdAndRequesterUserId(UUID teamId, UUID requesterUserId);
}
