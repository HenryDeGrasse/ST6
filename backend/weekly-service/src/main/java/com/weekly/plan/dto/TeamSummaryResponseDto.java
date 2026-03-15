package com.weekly.plan.dto;

import java.util.List;

/**
 * Paginated team summary for the manager dashboard.
 */
public record TeamSummaryResponseDto(
        String weekStart,
        List<TeamMemberSummaryResponse> users,
        ReviewStatusCountsResponse reviewStatusCounts,
        int page,
        int size,
        int totalElements,
        int totalPages
) {}
