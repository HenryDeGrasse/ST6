package com.weekly.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AiSuggestionFeedbackEntity}.
 */
@Repository
public interface AiSuggestionFeedbackRepository
        extends JpaRepository<AiSuggestionFeedbackEntity, UUID> {

    /**
     * Finds feedback for a specific suggestion by the given user within the org.
     * Used for upsert semantics in the suggestion-feedback endpoint.
     */
    Optional<AiSuggestionFeedbackEntity> findByOrgIdAndUserIdAndSuggestionId(
            UUID orgId, UUID userId, UUID suggestionId);

    /**
     * Returns all feedback records for the given user that were created after
     * {@code since}. Used by {@link DefaultNextWorkSuggestionService} to filter
     * recently declined suggestions within the 4-week suppression window.
     */
    List<AiSuggestionFeedbackEntity> findByOrgIdAndUserIdAndCreatedAtAfter(
            UUID orgId, UUID userId, Instant since);

    /**
     * Returns all feedback records for the given organisation that were created
     * after {@code since}. Used by the admin dashboard to compute org-wide
     * suggestion acceptance rates over a rolling window.
     */
    List<AiSuggestionFeedbackEntity> findByOrgIdAndCreatedAtAfter(UUID orgId, Instant since);
}
