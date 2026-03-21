package com.weekly.shared;

import java.math.BigDecimal;

/**
 * Strategic slack summary for an organisation's tracked outcome portfolio.
 *
 * <p>Produced by {@link UrgencyDataProvider} and consumed by downstream phases
 * (Phase 2/4/5) without depending on {@code com.weekly.urgency} internals.
 *
 * <p>In Phase 3 the calculation is org-wide; the manager dimension is reserved
 * for future per-team scoping.
 *
 * @param slackBand            high-level slack classification: {@code HIGH_SLACK},
 *                             {@code MODERATE_SLACK}, {@code LOW_SLACK}, or
 *                             {@code NO_SLACK}
 * @param strategicFocusFloor  computed strategic focus floor for the organisation,
 *                             in the range {@code 0.50}–{@code 0.95}
 * @param atRiskCount          number of outcomes in the {@code AT_RISK} urgency band
 * @param criticalCount        number of outcomes in the {@code CRITICAL} urgency band
 */
public record SlackInfo(
        String slackBand,
        BigDecimal strategicFocusFloor,
        int atRiskCount,
        int criticalCount
) {}
