package com.weekly.ai;

/**
 * A data-driven quality nudge surfaced before plan locking (Wave 1, step 5).
 *
 * <p>Nudges are returned by {@link PlanQualityService} and rendered to the user
 * pre-lock so they can act before committing their week.
 *
 * @param type     machine-readable nudge type (e.g., {@code "COVERAGE_GAP"})
 * @param message  human-readable explanation shown in the UI
 * @param severity one of {@code INFO}, {@code WARNING}, or {@code POSITIVE}
 */
public record QualityNudge(
        String type,
        String message,
        String severity
) {}
