package com.weekly.usermodel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link UserUpdatePatternEntity}.
 *
 * <p>Provides lookup methods used both for upsert logic (exact-match
 * lookup) and for surfacing the user's top-used phrases to the AI
 * option generator (ordered by frequency).
 */
@Repository
public interface UserUpdatePatternRepository
        extends JpaRepository<UserUpdatePatternEntity, UUID> {

    /**
     * Looks up the unique pattern record for the given combination of
     * organisation, user, category, and note text.
     *
     * <p>Used by {@link UserUpdatePatternService#recordPattern} to decide
     * whether to increment an existing counter or create a new record.
     *
     * @param orgId    the organisation ID
     * @param userId   the user ID
     * @param category the check-in category
     * @param noteText the verbatim note text
     * @return the matching entity, or empty if not yet seen
     */
    Optional<UserUpdatePatternEntity> findByOrgIdAndUserIdAndCategoryAndNoteText(
            UUID orgId,
            UUID userId,
            String category,
            String noteText
    );

    /**
     * Returns the user's most-frequently-used notes for a given category,
     * ordered by frequency descending.
     *
     * <p>The caller controls how many results are returned via {@code pageable}
     * (typically {@code PageRequest.of(0, limit)}).
     *
     * @param orgId    the organisation ID
     * @param userId   the user ID
     * @param category the check-in category
     * @param pageable page/size constraints (only the page content is returned)
     * @return list of pattern entities ordered by frequency descending
     */
    List<UserUpdatePatternEntity> findByOrgIdAndUserIdAndCategoryOrderByFrequencyDesc(
            UUID orgId,
            UUID userId,
            String category,
            Pageable pageable
    );

    /**
     * Returns org-level rollups for a category by summing learned pattern
     * frequencies across users.
     *
     * <p>Results are ordered deterministically by summed frequency descending,
     * then recency descending, then note text ascending.
     *
     * @param orgId    organisation to aggregate within
     * @param category category to filter by, may be null
     * @param pageable page/size constraints for top-N selection
     * @return aggregated team pattern rollups
     */
    @Query("""
            SELECT new com.weekly.usermodel.TeamPatternRollup(
                p.noteText,
                SUM(p.frequency),
                MAX(p.lastUsedAt)
            )
            FROM UserUpdatePatternEntity p
            WHERE p.orgId = :orgId
              AND ((:category IS NULL AND p.category IS NULL) OR p.category = :category)
              AND TRIM(p.noteText) <> ''
            GROUP BY p.noteText
            ORDER BY SUM(p.frequency) DESC, MAX(p.lastUsedAt) DESC, p.noteText ASC
            """)
    List<TeamPatternRollup> findTopTeamPatternsByOrgIdAndCategory(
            @Param("orgId") UUID orgId,
            @Param("category") String category,
            Pageable pageable
    );
}
