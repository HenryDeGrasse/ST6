package com.weekly.rcdo;

/**
 * A single RCDO search result for typeahead.
 */
public record RcdoSearchResult(
        String id,
        String name,
        String objectiveId,
        String objectiveName,
        String rallyCryId,
        String rallyCryName
) {}
