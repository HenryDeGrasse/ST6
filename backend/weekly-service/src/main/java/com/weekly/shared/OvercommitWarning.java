package com.weekly.shared;

import java.math.BigDecimal;

/**
 * Immutable value object carrying the result of an overcommitment check.
 *
 * @param level         severity of the overcommitment ({@link OvercommitLevel})
 * @param message       human-readable description of the warning (empty for {@code NONE})
 * @param adjustedTotal sum of bias-adjusted estimated hours across all commits
 * @param realisticCap  the user's realistic weekly capacity cap from their profile
 */
public record OvercommitWarning(
        OvercommitLevel level,
        String message,
        BigDecimal adjustedTotal,
        BigDecimal realisticCap) {
}
