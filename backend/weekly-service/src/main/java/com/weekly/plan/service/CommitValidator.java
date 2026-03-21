package com.weekly.plan.service;

import com.weekly.plan.domain.WeeklyCommitEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates a commit and returns a list of validation errors.
 *
 * <p>During DRAFT, these are inline warnings displayed in the UI.
 * At lock time, any errors block the transition.
 */
@Component
public class CommitValidator {

    /**
     * Computes validation errors for a single commit.
     */
    public List<CommitValidationError> validate(WeeklyCommitEntity commit) {
        List<CommitValidationError> errors = new ArrayList<>();

        if (commit.getChessPriority() == null) {
            errors.add(CommitValidationError.missingChessPriority());
        }

        boolean hasOutcome = commit.getOutcomeId() != null;
        boolean hasReason = commit.getNonStrategicReason() != null
                && !commit.getNonStrategicReason().isBlank();

        if (hasOutcome && hasReason) {
            errors.add(CommitValidationError.conflictingLink());
        } else if (!hasOutcome && !hasReason) {
            errors.add(CommitValidationError.missingRcdoOrReason());
        }

        return errors;
    }
}
