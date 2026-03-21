package com.weekly.capacity;

import java.util.List;

/**
 * API response DTO for the manager team-capacity view.
 *
 * <p>Returned by {@code GET /api/v1/team/capacity?weekStart=YYYY-MM-DD}.
 *
 * @param weekStart ISO-8601 date (Monday) for the week being queried
 * @param members   capacity summary for each direct report of the authenticated manager
 */
public record TeamCapacityResponse(
        String weekStart,
        List<TeamMemberCapacity> members) {
}
