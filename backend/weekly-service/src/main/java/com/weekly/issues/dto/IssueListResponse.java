package com.weekly.issues.dto;

import java.util.List;

/**
 * Paginated response DTO for listing backlog issues (Phase 6).
 */
public record IssueListResponse(
        List<IssueResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
