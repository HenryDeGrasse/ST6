package com.weekly.usermodel;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregated usage count for a normalized user update pattern over a time window.
 *
 * @param orgId              organisation that owns the pattern
 * @param userId             user who authored the typed notes
 * @param category           commit category associated with the notes, if any
 * @param noteText           normalized note text to upsert
 * @param frequencyIncrement number of matching typed notes in the aggregation window
 * @param lastUsedAt         latest source timestamp seen for this normalized note
 */
public record AggregatedPatternUsage(
        UUID orgId,
        UUID userId,
        String category,
        String noteText,
        int frequencyIncrement,
        Instant lastUsedAt
) {}
