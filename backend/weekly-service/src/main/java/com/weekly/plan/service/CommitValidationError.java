package com.weekly.plan.service;

/**
 * Represents a single validation error on a commit (inline warning during DRAFT,
 * blocking error at lock time).
 */
public record CommitValidationError(String code, String message) {

    public static CommitValidationError missingChessPriority() {
        return new CommitValidationError(
                "MISSING_CHESS_PRIORITY",
                "Chess priority is required"
        );
    }

    public static CommitValidationError missingRcdoOrReason() {
        return new CommitValidationError(
                "MISSING_RCDO_OR_REASON",
                "An RCDO link or non-strategic reason is required"
        );
    }

    public static CommitValidationError conflictingLink() {
        return new CommitValidationError(
                "CONFLICTING_LINK",
                "A commit cannot have both an RCDO link and a non-strategic reason"
        );
    }
}
