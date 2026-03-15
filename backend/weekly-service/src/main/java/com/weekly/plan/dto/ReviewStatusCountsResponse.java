package com.weekly.plan.dto;

/**
 * Aggregate counts of review statuses across team members for a given week.
 */
public record ReviewStatusCountsResponse(
        int pending,
        int approved,
        int changesRequested
) {}
