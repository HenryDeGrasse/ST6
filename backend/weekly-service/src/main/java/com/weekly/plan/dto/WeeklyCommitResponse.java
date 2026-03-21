package com.weekly.plan.dto;

import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.service.CommitValidationError;
import java.util.List;

/**
 * API response DTO for a weekly commit.
 */
public record WeeklyCommitResponse(
        String id,
        String weeklyPlanId,
        String title,
        String description,
        String chessPriority,
        String category,
        String outcomeId,
        String nonStrategicReason,
        String expectedResult,
        Double confidence,
        Double estimatedHours,
        String[] tags,
        String progressNotes,
        String snapshotRallyCryId,
        String snapshotRallyCryName,
        String snapshotObjectiveId,
        String snapshotObjectiveName,
        String snapshotOutcomeId,
        String snapshotOutcomeName,
        String carriedFromCommitId,
        int version,
        String createdAt,
        String updatedAt,
        List<ValidationErrorDto> validationErrors,
        WeeklyCommitActualResponse actual
) {

    public record ValidationErrorDto(String code, String message) {}

    /**
     * Creates a response without actuals data (backward-compatible).
     */
    public static WeeklyCommitResponse from(
            WeeklyCommitEntity entity,
            List<CommitValidationError> errors
    ) {
        return from(entity, errors, null);
    }

    /**
     * Creates a response with optional actuals data.
     *
     * @param entity      the commit entity
     * @param errors      inline validation errors
     * @param actualEntity optional actuals entity (may be null)
     */
    public static WeeklyCommitResponse from(
            WeeklyCommitEntity entity,
            List<CommitValidationError> errors,
            WeeklyCommitActualEntity actualEntity
    ) {
        return new WeeklyCommitResponse(
                entity.getId().toString(),
                entity.getWeeklyPlanId().toString(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getChessPriority() != null ? entity.getChessPriority().name() : null,
                entity.getCategory() != null ? entity.getCategory().name() : null,
                entity.getOutcomeId() != null ? entity.getOutcomeId().toString() : null,
                entity.getNonStrategicReason(),
                entity.getExpectedResult(),
                entity.getConfidence() != null ? entity.getConfidence().doubleValue() : null,
                entity.getEstimatedHours() != null ? entity.getEstimatedHours().doubleValue() : null,
                entity.getTags(),
                entity.getProgressNotes(),
                entity.getSnapshotRallyCryId() != null
                        ? entity.getSnapshotRallyCryId().toString() : null,
                entity.getSnapshotRallyCryName(),
                entity.getSnapshotObjectiveId() != null
                        ? entity.getSnapshotObjectiveId().toString() : null,
                entity.getSnapshotObjectiveName(),
                entity.getSnapshotOutcomeId() != null
                        ? entity.getSnapshotOutcomeId().toString() : null,
                entity.getSnapshotOutcomeName(),
                entity.getCarriedFromCommitId() != null
                        ? entity.getCarriedFromCommitId().toString() : null,
                entity.getVersion(),
                entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString(),
                errors.stream()
                        .map(e -> new ValidationErrorDto(e.code(), e.message()))
                        .toList(),
                actualEntity != null ? WeeklyCommitActualResponse.from(actualEntity) : null
        );
    }
}
