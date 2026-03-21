package com.weekly.plan.dto;

import com.weekly.plan.service.DraftFromHistoryService.DraftFromHistoryResult;
import com.weekly.plan.service.SuggestedCommit;
import java.util.List;

/**
 * Response body for the POST /api/v1/plans/draft-from-history endpoint.
 */
public record DraftFromHistoryResponse(
        String planId,
        List<SuggestedCommitDto> suggestedCommits
) {

    /**
     * API-facing DTO for a single suggested commit.
     *
     * <p>Mirrors {@link SuggestedCommit} but uses plain strings for all fields
     * so they can be serialised directly to JSON.
     */
    public record SuggestedCommitDto(
            String commitId,
            String title,
            String description,
            String chessPriority,
            String category,
            String outcomeId,
            String nonStrategicReason,
            String expectedResult,
            String source
    ) {
        /** Maps a service-layer {@link SuggestedCommit} to this DTO. */
        public static SuggestedCommitDto from(SuggestedCommit commit) {
            return new SuggestedCommitDto(
                    commit.commitId().toString(),
                    commit.title(),
                    commit.description(),
                    commit.chessPriority(),
                    commit.category(),
                    commit.outcomeId(),
                    commit.nonStrategicReason(),
                    commit.expectedResult(),
                    commit.source().name()
            );
        }
    }

    /** Creates a response from the service-layer result record. */
    public static DraftFromHistoryResponse from(DraftFromHistoryResult result) {
        return new DraftFromHistoryResponse(
                result.planId().toString(),
                result.suggestedCommits().stream()
                        .map(SuggestedCommitDto::from)
                        .toList()
        );
    }
}
