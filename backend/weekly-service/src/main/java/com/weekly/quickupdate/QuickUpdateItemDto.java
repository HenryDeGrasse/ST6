package com.weekly.quickupdate;

import com.weekly.plan.domain.ProgressStatus;
import com.weekly.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Represents a single commit check-in within a batch quick-update request.
 *
 * @param commitId the commit to check in against; must not be null
 * @param status   progress status string (must map to a {@link ProgressStatus}); must not be blank
 * @param note     optional free-text note; may be null or blank
 */
public record QuickUpdateItemDto(
        @NotNull UUID commitId,
        @NotBlank @ValueOfEnum(enumClass = ProgressStatus.class) String status,
        String note
) {}
