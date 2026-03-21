package com.weekly.plan.repository;

import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.ProgressNoteSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection used by user-model aggregation jobs to read recent progress-entry
 * notes together with the owning user and commit category.
 */
public record ProgressEntryPatternInput(
        UUID progressEntryId,
        UUID orgId,
        UUID ownerUserId,
        CommitCategory commitCategory,
        String note,
        ProgressNoteSource noteSource,
        String selectedSuggestionText,
        String selectedSuggestionSource,
        Instant createdAt
) {}
