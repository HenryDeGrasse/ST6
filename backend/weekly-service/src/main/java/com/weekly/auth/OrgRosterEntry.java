package com.weekly.auth;

import java.util.UUID;

/**
 * Lightweight org-roster entry used by scheduled agents and executive rollups.
 *
 * <p>Extends the direct-report-only org graph seam with enough information for
 * org-wide team grouping and timezone-aware scheduling without exposing any HRIS
 * implementation details.
 *
 * @param userId       the user ID
 * @param displayName  the best-available human-readable name
 * @param managerId    the user's current manager, or {@code null} when unknown / top-level
 * @param timeZone     the user's IANA timezone identifier, defaulting to {@code UTC}
 */
public record OrgRosterEntry(
        UUID userId,
        String displayName,
        UUID managerId,
        String timeZone
) {}
