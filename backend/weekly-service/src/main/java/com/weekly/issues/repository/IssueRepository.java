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
import org.springframework.transaction.annotation.Transactional;

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

    Page<IssueEntity> findAllByOrgIdAndStatus(UUID orgId, IssueStatus status, Pageable pageable);

    Page<IssueEntity> findAllByOrgIdAndStatusNot(UUID orgId, IssueStatus status, Pageable pageable);

    Page<IssueEntity> findAllByOrgIdAndTeamIdInAndStatus(
            UUID orgId,
            java.util.Collection<UUID> teamIds,
            IssueStatus status,
            Pageable pageable
    );

    Page<IssueEntity> findAllByOrgIdAndTeamIdInAndStatusNot(
            UUID orgId,
            java.util.Collection<UUID> teamIds,
            IssueStatus status,
            Pageable pageable
    );

    /** Returns distinct team IDs that have at least one issue in the given status. */
    @Query("SELECT DISTINCT i.teamId FROM IssueEntity i WHERE i.status = :status")
    List<UUID> findDistinctTeamIdsByStatus(@Param("status") IssueStatus status);

    /** Returns distinct team IDs that have at least one issue in any of the given statuses. */
    @Query("SELECT DISTINCT i.teamId FROM IssueEntity i WHERE i.status IN :statuses")
    List<UUID> findDistinctTeamIdsByStatusIn(@Param("statuses") java.util.Collection<IssueStatus> statuses);

    /** Org-scoped variant used by HyDE fallback so results never escape the caller org. */
    @Query("SELECT DISTINCT i.teamId FROM IssueEntity i WHERE i.orgId = :orgId AND i.status IN :statuses")
    List<UUID> findDistinctTeamIdsByOrgIdAndStatusIn(
            @Param("orgId") UUID orgId,
            @Param("statuses") java.util.Collection<IssueStatus> statuses
    );

    /** Returns all issues for a team whose status is in the given set. */
    @Query("SELECT i FROM IssueEntity i WHERE i.teamId = :teamId AND i.status IN :statuses")
    List<IssueEntity> findAllByTeamIdAndStatusIn(
            @Param("teamId") UUID teamId,
            @Param("statuses") java.util.Collection<IssueStatus> statuses);

    /**
     * Returns all non-archived issues that have never been successfully embedded
     * ({@code embedding_version = 0}).
     *
     * <p>Used by the admin backfill endpoint to find issues created before the
     * embedding pipeline was wired up.
     */
    List<IssueEntity> findAllByEmbeddingVersionAndStatusNot(int embeddingVersion, IssueStatus status);

    /** Org-scoped variant used by admin backfill so one org cannot process another org's issues. */
    List<IssueEntity> findAllByOrgIdAndEmbeddingVersionAndStatusNot(
            UUID orgId,
            int embeddingVersion,
            IssueStatus status
    );

    /**
     * Increments {@code embedding_version} by 1 for the given issue.
     *
     * <p>Called by the async embedding job after each successful embed so that the
     * backfill query ({@code embedding_version = 0}) correctly excludes already-
     * embedded issues.
     */
    @Modifying
    @Transactional
    @Query("UPDATE IssueEntity i SET i.embeddingVersion = i.embeddingVersion + 1 WHERE i.id = :issueId")
    void incrementEmbeddingVersion(@Param("issueId") UUID issueId);

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
