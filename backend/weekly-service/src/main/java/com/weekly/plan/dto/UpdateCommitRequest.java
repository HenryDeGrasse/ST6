package com.weekly.plan.dto;

import com.weekly.shared.validation.NullOrNotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for partially updating a weekly commit (PATCH).
 *
 * <p>All fields are optional. Only provided (non-null) fields are applied.
 * After LOCK, only {@code progressNotes} may be changed.
 */
public record UpdateCommitRequest(
        @NullOrNotBlank @Size(max = 500) String title,
        String description,
        String chessPriority,
        String category,
        String outcomeId,
        String nonStrategicReason,
        String expectedResult,
        Double confidence,
        String[] tags,
        String progressNotes
) {}
