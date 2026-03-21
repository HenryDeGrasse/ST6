package com.weekly.quickupdate;

import com.weekly.plan.dto.CheckInEntryResponse;
import java.util.List;

/**
 * Response DTO returned after a successful batch quick-update.
 *
 * @param updatedCount number of progress entries created
 * @param entries      the created progress entries
 */
public record QuickUpdateResponseDto(
        int updatedCount,
        List<CheckInEntryResponse> entries
) {}
