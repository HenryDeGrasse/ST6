package com.weekly.usermodel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for maintaining the per-user note-text frequency model.
 *
 * <p>Every time a user submits a check-in note the service is called to record
 * (or increment) the pattern. Downstream, {@code CheckInOptionService} reads
 * the top patterns for a given category to generate personalised quick-pick
 * suggestions on the Quick Update card.
 */
@Service
public class UserUpdatePatternService {

    private final UserUpdatePatternRepository repository;

    public UserUpdatePatternService(UserUpdatePatternRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a single use of {@code noteText} by {@code userId} in the given
     * {@code category}.
     *
     * <p>If a matching pattern already exists the frequency counter is
     * incremented and {@code lastUsedAt} is refreshed. Otherwise a new
     * record is created with {@code frequency=1}.
     *
     * @param orgId    the organisation the user belongs to
     * @param userId   the user who submitted the note
     * @param category the check-in category (e.g. "ON_TRACK", "BLOCKED")
     * @param noteText the verbatim note text to track
     */
    @Transactional
    public void recordPattern(UUID orgId, UUID userId, String category, String noteText) {
        Optional<UserUpdatePatternEntity> existing =
                repository.findByOrgIdAndUserIdAndCategoryAndNoteText(
                        orgId, userId, category, noteText);

        if (existing.isPresent()) {
            UserUpdatePatternEntity entity = existing.get();
            entity.setFrequency(entity.getFrequency() + 1);
            entity.setLastUsedAt(Instant.now());
            repository.save(entity);
        } else {
            UserUpdatePatternEntity entity = new UserUpdatePatternEntity(
                    UUID.randomUUID(), orgId, userId, category, noteText);
            repository.save(entity);
        }
    }

    /**
     * Returns the {@code limit} most-frequently-used notes for {@code userId}
     * in the given {@code category}, ordered by frequency descending.
     *
     * @param orgId    the organisation the user belongs to
     * @param userId   the user whose patterns to retrieve
     * @param category the check-in category to filter by
     * @param limit    maximum number of results to return
     * @return list of pattern entities ordered by frequency descending
     */
    @Transactional(readOnly = true)
    public List<UserUpdatePatternEntity> getTopPatterns(
            UUID orgId, UUID userId, String category, int limit) {
        return repository.findByOrgIdAndUserIdAndCategoryOrderByFrequencyDesc(
                orgId, userId, category, PageRequest.of(0, limit));
    }
}
