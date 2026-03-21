package com.weekly.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Snapshot of urgency information for a single RCDO outcome.
 *
 * <p>Produced by {@link UrgencyDataProvider} and consumed by downstream phases
 * (Phase 2/4/5) without depending on {@code com.weekly.urgency} internals.
 *
 * @param outcomeId           the RCDO outcome UUID
 * @param outcomeName         display name of the outcome
 * @param targetDate          the target completion date; {@code null} if not set
 * @param progressPct         current progress percentage (0–100); {@code null} if uncomputed
 * @param expectedProgressPct expected progress at today's date given linear progression;
 *                            {@code null} if no target date is set
 * @param urgencyBand         urgency classification: {@code NO_TARGET},
 *                            {@code ON_TRACK}, {@code NEEDS_ATTENTION},
 *                            {@code AT_RISK}, or {@code CRITICAL}
 * @param daysRemaining       calendar days from today to the target date; negative if overdue;
 *                            {@link Long#MIN_VALUE} if no target date is set
 */
public record UrgencyInfo(
        UUID outcomeId,
        String outcomeName,
        LocalDate targetDate,
        BigDecimal progressPct,
        BigDecimal expectedProgressPct,
        String urgencyBand,
        long daysRemaining
) {}
