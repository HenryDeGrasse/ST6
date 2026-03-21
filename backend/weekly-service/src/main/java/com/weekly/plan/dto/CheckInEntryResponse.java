package com.weekly.plan.dto;

import com.weekly.plan.domain.ProgressEntryEntity;

/**
 * API response DTO for a single progress check-in entry.
 */
public record CheckInEntryResponse(
        String id,
        String commitId,
        String status,
        String note,
        String createdAt
) {

    /**
     * Builds a response DTO from the given entity.
     *
     * @param entity the persisted progress entry
     * @return the response DTO
     */
    public static CheckInEntryResponse from(ProgressEntryEntity entity) {
        return new CheckInEntryResponse(
                entity.getId().toString(),
                entity.getCommitId().toString(),
                entity.getStatus().name(),
                entity.getNote(),
                entity.getCreatedAt().toString()
        );
    }
}
