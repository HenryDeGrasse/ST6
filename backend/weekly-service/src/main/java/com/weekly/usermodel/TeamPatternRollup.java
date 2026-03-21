package com.weekly.usermodel;

import java.time.Instant;

/**
 * Aggregated org-level rollup of learned user update patterns for a category.
 *
 * @param noteText        normalized learned note text
 * @param totalFrequency  summed frequency across all users in the org/category
 * @param lastUsedAt      most recent usage timestamp across matching rows
 */
public record TeamPatternRollup(
        String noteText,
        long totalFrequency,
        Instant lastUsedAt
) {}
