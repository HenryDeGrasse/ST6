package com.weekly.plan.service;

import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.dto.CheckInEntryResponse;
import com.weekly.plan.dto.CheckInHistoryResponse;
import com.weekly.plan.dto.CheckInRequest;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.shared.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CheckInService}.
 *
 * <p>Allows users to append structured micro-updates ({@link ProgressEntryEntity})
 * to a weekly commit. The history is append-only — entries are never modified or deleted.
 *
 * <p>A check-in can be added at any point while the commit is accessible to
 * the authenticated user; plan-state enforcement is intentionally lenient
 * (check-ins are advisory and non-binding).
 */
@Service
public class DefaultCheckInService implements CheckInService {

    private final WeeklyCommitRepository commitRepository;
    private final ProgressEntryRepository progressEntryRepository;

    public DefaultCheckInService(
            WeeklyCommitRepository commitRepository,
            ProgressEntryRepository progressEntryRepository
    ) {
        this.commitRepository = commitRepository;
        this.progressEntryRepository = progressEntryRepository;
    }

    /**
     * Appends a new check-in entry to the given commit.
     *
     * @param orgId    the organisation from the auth context
     * @param commitId the commit to check in against
     * @param request  the check-in data (status + optional note)
     * @return the newly created entry
     * @throws CommitNotFoundException     if the commit does not exist or belongs to a different org
     * @throws PlanValidationException     if the status value is not a valid {@link ProgressStatus}
     */
    @Transactional
    public CheckInEntryResponse addCheckIn(UUID orgId, UUID commitId, CheckInRequest request) {
        // Verify the commit exists and belongs to the org
        commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        ProgressStatus status = parseStatus(request.status());
        String note = request.note() != null ? request.note() : "";

        ProgressEntryEntity entry = new ProgressEntryEntity(
                UUID.randomUUID(), orgId, commitId, status, note
        );
        progressEntryRepository.save(entry);

        return CheckInEntryResponse.from(entry);
    }

    /**
     * Returns the complete check-in history for a commit, ordered oldest-first.
     *
     * @param orgId    the organisation from the auth context
     * @param commitId the commit whose history to retrieve
     * @return the append-only history
     * @throws CommitNotFoundException if the commit does not exist or belongs to a different org
     */
    @Transactional(readOnly = true)
    public CheckInHistoryResponse getHistory(UUID orgId, UUID commitId) {
        // Verify the commit exists and belongs to the org
        commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        List<CheckInEntryResponse> entries =
                progressEntryRepository
                        .findByOrgIdAndCommitIdOrderByCreatedAtAsc(orgId, commitId)
                        .stream()
                        .map(CheckInEntryResponse::from)
                        .toList();

        return new CheckInHistoryResponse(commitId.toString(), entries);
    }

    // ── Internal helpers ─────────────────────────────────────

    private ProgressStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request validation failed",
                    List.of(Map.of(
                            "field", "status",
                            "message", "must not be blank"
                    ))
            );
        }

        try {
            return ProgressStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request validation failed",
                    List.of(Map.of(
                            "field", "status",
                            "message", "must be one of ON_TRACK, AT_RISK, BLOCKED, DONE_EARLY"
                    ))
            );
        }
    }
}
