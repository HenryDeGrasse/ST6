package com.weekly.issues.repository;

import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link IssueEntity} (Phase 6). */
public interface IssueRepository extends JpaRepository<IssueEntity, UUID> {

    List<IssueEntity> findAllByTeamIdAndStatus(UUID teamId, IssueStatus status);

    List<IssueEntity> findAllByOrgIdAndAssigneeUserId(UUID orgId, UUID assigneeUserId);

    /**
     * Atomically increments the team's issue sequence and returns the new value.
     *
     * <p>The UPDATE acquires a row-level lock on the teams row, preventing
     * concurrent inserts from receiving the same sequence number. The service
     * layer must call this inside a transaction before inserting the issue.
     */
    @Modifying
    @Query(value = "UPDATE teams SET issue_sequence = issue_sequence + 1 "
            + "WHERE id = :teamId RETURNING issue_sequence",
            nativeQuery = true)
    int incrementAndGetIssueSequence(@Param("teamId") UUID teamId);
}
