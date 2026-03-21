package com.weekly.digest;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service that aggregates weekly plan data for a manager and produces a
 * {@link DigestPayload} suitable for rendering in a digest notification.
 *
 * <p>The payload covers:
 * <ul>
 *   <li>Team plan status counts (reconciled / locked / draft / stale)</li>
 *   <li>Manager review queue size</li>
 *   <li>Attention items: carry-forward streaks, late locks</li>
 *   <li>Highlights: RCDO alignment rate, items completed ahead of schedule</li>
 * </ul>
 */
public interface DigestService {

    /**
     * Builds the digest payload for the given manager and week.
     *
     * @param orgId     the organisation ID
     * @param managerId the manager's user ID
     * @param weekStart the Monday of the week to summarise
     * @return aggregated digest data; never {@code null}
     */
    DigestPayload buildDigestPayload(UUID orgId, UUID managerId, LocalDate weekStart);
}
