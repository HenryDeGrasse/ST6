package com.weekly.plan.repository;

import com.weekly.plan.domain.ProgressEntryEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ProgressEntryEntity}.
 *
 * <p>Entries are append-only; no update or delete methods are exposed.
 */
@Repository
public interface ProgressEntryRepository extends JpaRepository<ProgressEntryEntity, UUID> {

    /**
     * Returns all check-in entries for the given commit within the organisation,
     * ordered by creation time ascending (oldest first).
     *
     * @param orgId    the organisation ID
     * @param commitId the commit ID
     * @return ordered list of progress entries
     */
    List<ProgressEntryEntity> findByOrgIdAndCommitIdOrderByCreatedAtAsc(UUID orgId, UUID commitId);

    /**
     * Returns all check-in entries for the given set of commits within the organisation,
     * ordered by creation time ascending (oldest first).
     *
     * <p>Used for batch retrieval to avoid N+1 queries when loading check-in history
     * for all commits in a weekly plan at once.
     *
     * @param orgId     the organisation ID
     * @param commitIds the commit IDs to fetch entries for
     * @return ordered list of progress entries for the given commits
     */
    List<ProgressEntryEntity> findByOrgIdAndCommitIdInOrderByCreatedAtAsc(UUID orgId, List<UUID> commitIds);

    /**
     * Returns recent non-blank progress-entry notes enriched with commit category
     * and plan owner for user-model aggregation jobs.
     *
     * @param since inclusive lower bound on entry creation time
     * @return recent pattern-input rows ordered by creation time ascending
     */
    @Query("""
            SELECT new com.weekly.plan.repository.ProgressEntryPatternInput(
                pe.id,
                pe.orgId,
                wp.ownerUserId,
                wc.category,
                pe.note,
                pe.noteSource,
                pe.selectedSuggestionText,
                pe.selectedSuggestionSource,
                pe.createdAt
            )
            FROM ProgressEntryEntity pe
            JOIN WeeklyCommitEntity wc
                ON wc.id = pe.commitId
               AND wc.orgId = pe.orgId
            JOIN WeeklyPlanEntity wp
                ON wp.id = wc.weeklyPlanId
               AND wp.orgId = wc.orgId
            WHERE pe.createdAt >= :since
              AND TRIM(pe.note) <> ''
            ORDER BY pe.createdAt ASC
            """)
    List<ProgressEntryPatternInput> findPatternInputsCreatedSince(@Param("since") Instant since);
}
