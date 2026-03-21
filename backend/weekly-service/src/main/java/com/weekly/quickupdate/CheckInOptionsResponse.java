package com.weekly.quickupdate;

import com.weekly.plan.domain.ProgressStatus;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for the check-in options endpoint.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@code status} — service status ({@code "ok"} or {@code "unavailable"})</li>
 *   <li>{@code statusOptions} — ordered list of valid progress status strings</li>
 *   <li>{@code progressOptions} — AI- and pattern-derived option items</li>
 * </ul>
 *
 * <p>Use {@link #empty()} as the safe fallback when the LLM is unavailable or the
 * commit cannot be found (PRD §4 fallback contract).
 *
 * @param status          service status string
 * @param statusOptions   valid progress status strings derived from {@link ProgressStatus}
 * @param progressOptions the generated check-in option items
 */
public record CheckInOptionsResponse(
        String status,
        List<String> statusOptions,
        List<CheckInOptionItem> progressOptions
) {

    /**
     * Safe fallback response used when the LLM is unavailable or the commit
     * is not found.
     *
     * @return a response with status {@code "ok"}, all {@link ProgressStatus}
     *         values as status options, and an empty progress-options list
     */
    public static CheckInOptionsResponse empty() {
        List<String> statusOptions = Arrays.stream(ProgressStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return new CheckInOptionsResponse("ok", statusOptions, List.of());
    }
}
