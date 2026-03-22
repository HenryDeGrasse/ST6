package com.weekly.issues.dto;

import java.util.List;

/**
 * API response DTO for issue detail including activity log (Phase 6).
 */
public record IssueDetailResponse(
        IssueResponse issue,
        List<IssueActivityResponse> activities
) {}
