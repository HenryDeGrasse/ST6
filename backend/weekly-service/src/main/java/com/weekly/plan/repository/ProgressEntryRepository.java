package com.weekly.plan.repository;

import com.weekly.plan.domain.ProgressEntryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
