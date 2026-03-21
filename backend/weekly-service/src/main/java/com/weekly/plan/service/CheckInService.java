package com.weekly.plan.service;

import com.weekly.plan.dto.CheckInEntryResponse;
import com.weekly.plan.dto.CheckInHistoryResponse;
import com.weekly.plan.dto.CheckInRequest;
import java.util.UUID;

/**
 * Service contract for Quick Daily Check-In operations.
 *
 * <p>Allows users to append structured micro-updates to a weekly commit and
 * to retrieve the append-only history of those updates.
 */
public interface CheckInService {

    /**
     * Appends a new check-in entry to the given commit.
     *
     * @param orgId    the organisation from the auth context
     * @param commitId the commit to check in against
     * @param request  the check-in data (status + optional note)
     * @return the newly created entry
     */
    CheckInEntryResponse addCheckIn(UUID orgId, UUID commitId, CheckInRequest request);

    /**
     * Returns the complete check-in history for a commit, ordered oldest-first.
     *
     * @param orgId    the organisation from the auth context
     * @param commitId the commit whose history to retrieve
     * @return the append-only history
     */
    CheckInHistoryResponse getHistory(UUID orgId, UUID commitId);
}
