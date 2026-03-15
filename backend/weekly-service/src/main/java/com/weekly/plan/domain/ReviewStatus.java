package com.weekly.plan.domain;

/**
 * Manager review status (orthogonal to plan state).
 */
public enum ReviewStatus {
    REVIEW_NOT_APPLICABLE,
    REVIEW_PENDING,
    CHANGES_REQUESTED,
    APPROVED
}
