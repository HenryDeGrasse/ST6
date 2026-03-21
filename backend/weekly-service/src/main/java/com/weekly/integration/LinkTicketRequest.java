package com.weekly.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/integrations/link-ticket}.
 *
 * <p>Links a weekly commit to an external issue-tracker ticket.
 * When the commit's title or description is empty, they will be
 * auto-populated from the fetched ticket data.
 */
public record LinkTicketRequest(

        @NotNull(message = "commitId must not be null")
        UUID commitId,

        @NotBlank(message = "provider must not be blank")
        @Pattern(regexp = "JIRA|LINEAR",
                message = "provider must be one of: JIRA, LINEAR")
        String provider,

        @NotBlank(message = "externalTicketId must not be blank")
        String externalTicketId
) {
}
