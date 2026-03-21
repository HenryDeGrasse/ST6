package com.weekly.usermodel;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that exposes org-level learned update patterns for category fallback.
 *
 * <p>These patterns are aggregated from {@code user_update_patterns}, not raw
 * progress entries, so request-time fallback stays cheap and builds on the
 * nightly learning loop.
 */
@Service
public class TeamPatternService {

    private final UserUpdatePatternRepository repository;

    public TeamPatternService(UserUpdatePatternRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the top learned note texts used across the organisation for the
     * given category.
     *
     * <p>Ordering is deterministic: highest summed frequency first, then most
     * recent usage, then note text ascending.
     *
     * @param orgId    organisation to roll up
     * @param category commit category to filter by, may be null
     * @param limit    maximum number of note texts to return
     * @return top note texts for org-level fallback suggestions
     */
    @Transactional(readOnly = true)
    public List<String> getTopPatterns(UUID orgId, String category, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return repository.findTopTeamPatternsByOrgIdAndCategory(
                        orgId,
                        category,
                        PageRequest.of(0, limit))
                .stream()
                .map(TeamPatternRollup::noteText)
                .filter(noteText -> noteText != null && !noteText.isBlank())
                .toList();
    }
}
