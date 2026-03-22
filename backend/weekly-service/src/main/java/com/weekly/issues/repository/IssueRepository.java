package com.weekly.issues.repository;

import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link IssueEntity} (Phase 6). */
public interface IssueRepository extends JpaRepository<IssueEntity, UUID> {

    Optional<IssueEntity> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<IssueEntity> findByOrgIdAndIssueKey(UUID orgId, String issueKey);

    List<IssueEntity> findAllByTeamIdAndStatus(UUID teamId, IssueStatus status);

    List<IssueEntity> findAllByOrgIdAndAssigneeUserId(UUID orgId, UUID assigneeUserId);

    List<IssueEntity> findAllByOrgIdAndOutcomeId(UUID orgId, UUID outcomeId);

    Page<IssueEntity> findAllByTeamIdAndStatusNot(UUID teamId, IssueStatus status, Pageable pageable);

    Page<IssueEntity> findAllByTeamId(UUID teamId, Pageable pageable);

    Page<IssueEntity> findAllByTeamIdAndStatus(UUID teamId, IssueStatus status, Pageable pageable);

    /**
     * Atomically increments the team's issue sequence and returns the new value.
     *
     * <p>The UPDATE acquires a row-level lock on the teams row, preventing
     * concurrent inserts from receiving the same sequence number. The service
     * layer must call this inside a transaction before inserting the issue.
     *
     * <p>Returns 0 when no team row is matched (team not found).
     */
    @Modifying
    @Query(value = "UPDATE teams SET issue_sequence = issue_sequence + 1 "
            + "WHERE id = :teamId RETURNING issue_sequence",
            nativeQuery = true)
    int incrementAndGetIssueSequence(@Param("teamId") UUID teamId);
}
