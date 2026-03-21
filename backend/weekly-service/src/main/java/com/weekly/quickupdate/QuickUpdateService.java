package com.weekly.quickupdate;

import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.CheckInEntryResponse;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitNotFoundException;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanNotFoundException;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ErrorCode;
import com.weekly.usermodel.UserUpdatePatternService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that handles the batch check-in (Quick Update) flow.
 *
 * <p>Validates plan ownership and state, then atomically creates
 * {@link ProgressEntryEntity} records for each item in the batch.
 * Non-empty notes are also recorded in the user-update pattern model
 * to power personalised option suggestions on the Quick Update card.
 */
@Service
public class QuickUpdateService {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final UserUpdatePatternService userUpdatePatternService;

    public QuickUpdateService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            ProgressEntryRepository progressEntryRepository,
            UserUpdatePatternService userUpdatePatternService
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.userUpdatePatternService = userUpdatePatternService;
    }

    /**
     * Atomically check-in a batch of commits for a weekly plan.
     *
     * <ol>
     *   <li>Fetch the plan; throw {@link PlanNotFoundException} if absent.</li>
     *   <li>Verify the plan belongs to the authenticated user; throw
     *       {@link PlanAccessForbiddenException} otherwise.</li>
     *   <li>Verify the plan is in LOCKED or RECONCILING state; throw
     *       {@link PlanStateException} otherwise.</li>
     *   <li>Fetch all commits for the plan and build a by-ID lookup map.</li>
     *   <li>For each update item create a {@link ProgressEntryEntity} and persist it.</li>
     *   <li>Record non-empty notes in the user-update pattern model.</li>
     *   <li>Return a {@link QuickUpdateResponseDto} with the created entries.</li>
     * </ol>
     *
     * @param orgId   the organisation from the auth context
     * @param userId  the authenticated user
     * @param planId  the plan to check in against
     * @param updates the list of per-commit updates
     * @return the batch check-in response
     * @throws PlanNotFoundException         if the plan does not exist
     * @throws PlanAccessForbiddenException  if the plan belongs to a different user
     * @throws PlanStateException            if the plan is not LOCKED or RECONCILING
     * @throws CommitNotFoundException       if any update item references an unknown commit
     * @throws PlanValidationException       if any status value is invalid
     */
    @Transactional
    public QuickUpdateResponseDto batchCheckIn(
            UUID orgId,
            UUID userId,
            UUID planId,
            List<QuickUpdateItemDto> updates
    ) {
        // 1. Fetch and verify plan existence
        WeeklyPlanEntity plan = planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));

        // 2. Verify ownership
        if (!plan.getOwnerUserId().equals(userId)) {
            throw new PlanAccessForbiddenException("Plan does not belong to user");
        }

        // 3. Verify plan state (must be LOCKED or RECONCILING)
        PlanState state = plan.getState();
        if (state != PlanState.LOCKED && state != PlanState.RECONCILING) {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Plan must be LOCKED or RECONCILING for quick update",
                    state.name()
            );
        }

        // 4. Fetch all commits for the plan and build a by-ID map
        Map<UUID, WeeklyCommitEntity> commitMap =
                commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId).stream()
                        .collect(Collectors.toMap(WeeklyCommitEntity::getId, Function.identity()));

        // 5 & 6. Process each update item
        List<ProgressEntryEntity> entries = new ArrayList<>(updates.size());

        for (QuickUpdateItemDto item : updates) {
            // Validate commitId exists in the plan
            WeeklyCommitEntity commit = commitMap.get(item.commitId());
            if (commit == null) {
                throw new CommitNotFoundException(item.commitId());
            }

            // Parse status (same helper as DefaultCheckInService)
            ProgressStatus status = parseStatus(item.status());

            // Create and persist the progress entry
            ProgressEntryEntity entry = new ProgressEntryEntity(
                    UUID.randomUUID(), orgId, item.commitId(), status,
                    item.note() != null ? item.note() : ""
            );
            progressEntryRepository.save(entry);
            entries.add(entry);

            // Record non-empty notes in the user-update pattern model
            String note = item.note();
            if (note != null && !note.isBlank()) {
                String category = commit.getCategory() != null
                        ? commit.getCategory().name()
                        : null;
                userUpdatePatternService.recordPattern(orgId, userId, category, note);
            }
        }

        // 7. Build and return the response
        List<CheckInEntryResponse> entryResponses = entries.stream()
                .map(CheckInEntryResponse::from)
                .toList();

        return new QuickUpdateResponseDto(entries.size(), entryResponses);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

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
