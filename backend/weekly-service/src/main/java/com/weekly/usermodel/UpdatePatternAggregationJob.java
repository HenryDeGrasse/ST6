package com.weekly.usermodel;

import com.weekly.plan.domain.ProgressNoteSource;
import com.weekly.plan.repository.ProgressEntryPatternInput;
import com.weekly.plan.repository.ProgressEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job that aggregates recent typed quick-update notes into
 * {@code user_update_patterns}.
 *
 * <p>The job scans a recent window of progress entries, excludes blank notes and
 * suggestion-accepted notes, normalizes remaining typed notes, groups them by
 * org + user + category + note text, and delegates upserts to
 * {@link UserUpdatePatternService}. Successful runs persist a per-organisation
 * checkpoint so overlapping lookback windows do not recount the same progress
 * entries after later executions or worker restarts.
 *
 * <p>Enabled via {@code weekly.usermodel.update-pattern-aggregation.enabled=true}.
 * Defaults to {@code false} so the job is inactive outside worker environments.
 */
@Component
@ConditionalOnProperty(
        name = "weekly.usermodel.update-pattern-aggregation.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class UpdatePatternAggregationJob {

    private static final Logger LOG = LoggerFactory.getLogger(UpdatePatternAggregationJob.class);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Recent lookback window scanned on each nightly run. */
    static final int LOOKBACK_DAYS = 30;

    private final ProgressEntryRepository progressEntryRepository;
    private final UserUpdatePatternService userUpdatePatternService;
    private final UpdatePatternAggregationCheckpointRepository checkpointRepository;
    private final Clock clock;

    @Autowired
    public UpdatePatternAggregationJob(
            ProgressEntryRepository progressEntryRepository,
            UserUpdatePatternService userUpdatePatternService,
            UpdatePatternAggregationCheckpointRepository checkpointRepository
    ) {
        this(progressEntryRepository, userUpdatePatternService, checkpointRepository, Clock.systemUTC());
    }

    /** Package-private constructor for unit tests (injectable clock). */
    UpdatePatternAggregationJob(
            ProgressEntryRepository progressEntryRepository,
            UserUpdatePatternService userUpdatePatternService,
            UpdatePatternAggregationCheckpointRepository checkpointRepository,
            Clock clock
    ) {
        this.progressEntryRepository = progressEntryRepository;
        this.userUpdatePatternService = userUpdatePatternService;
        this.checkpointRepository = checkpointRepository;
        this.clock = clock;
    }

    /**
     * Aggregates recent typed progress-entry notes into user update patterns.
     *
     * <p>Runs nightly at 2 AM UTC. Failures are isolated per organisation so one
     * org cannot block updates for others.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void aggregateRecentPatterns() {
        Instant defaultSince = Instant.now(clock).minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        Instant globalSince = checkpointRepository.findAll().stream()
                .map(UpdatePatternAggregationCheckpointEntity::getLastAggregatedAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(defaultSince);

        List<ProgressEntryPatternInput> recentInputs =
                progressEntryRepository.findPatternInputsCreatedSince(globalSince);

        if (recentInputs.isEmpty()) {
            LOG.debug("UpdatePatternAggregationJob: no recent progress-entry notes found, skipping");
            return;
        }

        Map<UUID, List<ProgressEntryPatternInput>> typedInputsByOrg = recentInputs.stream()
                .filter(this::isTypedNoteCandidate)
                .collect(java.util.stream.Collectors.groupingBy(
                        ProgressEntryPatternInput::orgId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        if (typedInputsByOrg.isEmpty()) {
            LOG.debug("UpdatePatternAggregationJob: no typed progress-entry notes to aggregate");
            return;
        }

        LOG.info(
                "UpdatePatternAggregationJob: aggregating update patterns for {} org(s)",
                typedInputsByOrg.size()
        );

        int updatedOrgs = 0;
        for (Map.Entry<UUID, List<ProgressEntryPatternInput>> entry : typedInputsByOrg.entrySet()) {
            UUID orgId = entry.getKey();
            Instant lastSuccessfulAt = checkpointRepository.findByOrgId(orgId)
                    .map(UpdatePatternAggregationCheckpointEntity::getLastAggregatedAt)
                    .orElse(null);
            List<ProgressEntryPatternInput> orgInputs = entry.getValue().stream()
                    .filter(input -> isUnprocessedForOrg(input, lastSuccessfulAt, defaultSince))
                    .toList();
            List<AggregatedPatternUsage> aggregatedPatterns = aggregateOrgInputs(orgInputs);
            if (aggregatedPatterns.isEmpty()) {
                continue;
            }

            try {
                userUpdatePatternService.upsertAggregatedPatterns(aggregatedPatterns);
                saveCheckpoint(orgId, orgInputs, lastSuccessfulAt, defaultSince);
                updatedOrgs++;
            } catch (Exception ex) {
                LOG.warn(
                        "UpdatePatternAggregationJob: failed to aggregate patterns for org {}: {}",
                        orgId,
                        ex.getMessage(),
                        ex
                );
            }
        }

        LOG.info(
                "UpdatePatternAggregationJob: aggregation complete — {} org(s) updated",
                updatedOrgs
        );
    }

    private void saveCheckpoint(
            UUID orgId,
            List<ProgressEntryPatternInput> orgInputs,
            Instant lastSuccessfulAt,
            Instant defaultSince
    ) {
        Instant nextCheckpoint = orgInputs.stream()
                .map(ProgressEntryPatternInput::createdAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(lastSuccessfulAt == null ? defaultSince : lastSuccessfulAt);

        UpdatePatternAggregationCheckpointEntity checkpoint = checkpointRepository.findByOrgId(orgId)
                .orElseGet(() -> new UpdatePatternAggregationCheckpointEntity(orgId, nextCheckpoint));
        checkpoint.setLastAggregatedAt(nextCheckpoint);
        checkpointRepository.save(checkpoint);
    }

    private List<AggregatedPatternUsage> aggregateOrgInputs(List<ProgressEntryPatternInput> orgInputs) {
        Map<PatternAggregationKey, PatternAggregate> aggregated = new LinkedHashMap<>();

        for (ProgressEntryPatternInput input : orgInputs) {
            String normalizedNote = normalizeNote(input.note());
            if (normalizedNote.isBlank()) {
                continue;
            }

            String category = input.commitCategory() != null ? input.commitCategory().name() : null;
            PatternAggregationKey key = new PatternAggregationKey(
                    input.orgId(),
                    input.ownerUserId(),
                    category,
                    normalizedNote
            );
            aggregated.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new PatternAggregate(1, input.createdAt());
                }
                return existing.merge(input.createdAt());
            });
        }

        return aggregated.entrySet().stream()
                .map(entry -> new AggregatedPatternUsage(
                        entry.getKey().orgId(),
                        entry.getKey().userId(),
                        entry.getKey().category(),
                        entry.getKey().noteText(),
                        entry.getValue().frequency(),
                        entry.getValue().lastUsedAt()))
                .toList();
    }

    private boolean isTypedNoteCandidate(ProgressEntryPatternInput input) {
        return input != null
                && input.noteSource() == ProgressNoteSource.USER_TYPED
                && input.note() != null
                && !input.note().isBlank();
    }

    private boolean isUnprocessedForOrg(
            ProgressEntryPatternInput input,
            Instant lastSuccessfulAt,
            Instant defaultSince
    ) {
        if (input == null || input.createdAt() == null) {
            return false;
        }
        if (lastSuccessfulAt == null) {
            return !input.createdAt().isBefore(defaultSince);
        }
        return input.createdAt().isAfter(lastSuccessfulAt);
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return "";
        }
        return WHITESPACE.matcher(note.trim()).replaceAll(" ");
    }

    private record PatternAggregationKey(UUID orgId, UUID userId, String category, String noteText) {
    }

    private record PatternAggregate(int frequency, Instant lastUsedAt) {
        private PatternAggregate merge(Instant candidateLastUsedAt) {
            Instant mergedLastUsedAt = candidateLastUsedAt != null
                    && (lastUsedAt == null || candidateLastUsedAt.isAfter(lastUsedAt))
                    ? candidateLastUsedAt
                    : lastUsedAt;
            return new PatternAggregate(frequency + 1, mergedLastUsedAt);
        }
    }
}
