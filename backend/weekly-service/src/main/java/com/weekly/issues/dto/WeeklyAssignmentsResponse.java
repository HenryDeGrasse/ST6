package com.weekly.issues.dto;

import java.util.List;

/**
 * Response DTO listing all assignments for a weekly plan (Phase 6).
 */
public record WeeklyAssignmentsResponse(List<WeeklyAssignmentResponse> assignments) {}
