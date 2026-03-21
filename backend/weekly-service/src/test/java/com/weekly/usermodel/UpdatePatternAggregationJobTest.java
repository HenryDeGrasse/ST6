package com.weekly.usermodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.ProgressNoteSource;
import com.weekly.plan.repository.ProgressEntryPatternInput;
import com.weekly.plan.repository.ProgressEntryRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Unit tests for {@link UpdatePatternAggregationJob}.
 */
class UpdatePatternAggregationJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final Instant WINDOW_START = FIXED_NOW.minusSeconds(
            UpdatePatternAggregationJob.LOOKBACK_DAYS * 24L * 60L * 60L);

    private ProgressEntryRepository progressEntryRepository;
    private UserUpdatePatternService userUpdatePatternService;
    private UpdatePatternAggregationCheckpointRepository checkpointRepository;
    private UpdatePatternAggregationJob job;

    @BeforeEach
    void setUp() {
        progressEntryRepository = mock(ProgressEntryRepository.class);
        userUpdatePatternService = mock(UserUpdatePatternService.class);
        checkpointRepository = mock(UpdatePatternAggregationCheckpointRepository.class);
        when(checkpointRepository.findAll()).thenReturn(List.of());
        job = new UpdatePatternAggregationJob(
                progressEntryRepository,
                userUpdatePatternService,
                checkpointRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void aggregatesTypedNotesByOrgUserCategoryAndNormalizedText() {
        UUID orgIdOne = UUID.randomUUID();
        UUID orgIdTwo = UUID.randomUUID();
        UUID userIdOne = UUID.randomUUID();
        UUID userIdTwo = UUID.randomUUID();

        when(progressEntryRepository.findPatternInputsCreatedSince(WINDOW_START)).thenReturn(List.of(
                input(
                        orgIdOne,
                        userIdOne,
                        CommitCategory.DELIVERY,
                        "  Wrapped   API integration  ",
                        ProgressNoteSource.USER_TYPED,
                        Instant.parse("2026-03-20T10:00:00Z")
                ),
                input(
                        orgIdOne,
                        userIdOne,
                        CommitCategory.DELIVERY,
                        "Wrapped API integration",
                        ProgressNoteSource.USER_TYPED,
                        Instant.parse("2026-03-21T09:00:00Z")
                ),
                input(
                        orgIdOne,
                        userIdOne,
                        CommitCategory.DELIVERY,
                        "Wrapped API integration",
                        ProgressNoteSource.SUGGESTION_ACCEPTED,
                        Instant.parse("2026-03-21T11:00:00Z")
                ),
                input(
                        orgIdOne,
                        userIdOne,
                        CommitCategory.DELIVERY,
                        "   ",
                        ProgressNoteSource.USER_TYPED,
                        Instant.parse("2026-03-21T12:00:00Z")
                ),
                input(
                        orgIdTwo,
                        userIdTwo,
                        CommitCategory.OPERATIONS,
                        "Escalated rollout blocker",
                        ProgressNoteSource.USER_TYPED,
                        Instant.parse("2026-03-21T08:30:00Z")
                )
        ));
        when(checkpointRepository.findByOrgId(any())).thenReturn(Optional.empty());
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.aggregateRecentPatterns();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AggregatedPatternUsage>> captor =
                ArgumentCaptor.forClass((Class<List<AggregatedPatternUsage>>) (Class<?>) List.class);
        verify(userUpdatePatternService, times(2)).upsertAggregatedPatterns(captor.capture());

        List<List<AggregatedPatternUsage>> captured = captor.getAllValues();
        assertEquals(2, captured.size());

        AggregatedPatternUsage orgOneUsage = captured.stream()
                .flatMap(List::stream)
                .filter(usage -> usage.orgId().equals(orgIdOne))
                .findFirst()
                .orElseThrow();
        assertEquals(userIdOne, orgOneUsage.userId());
        assertEquals("DELIVERY", orgOneUsage.category());
        assertEquals("Wrapped API integration", orgOneUsage.noteText());
        assertEquals(2, orgOneUsage.frequencyIncrement());
        assertEquals(Instant.parse("2026-03-21T09:00:00Z"), orgOneUsage.lastUsedAt());

        AggregatedPatternUsage orgTwoUsage = captured.stream()
                .flatMap(List::stream)
                .filter(usage -> usage.orgId().equals(orgIdTwo))
                .findFirst()
                .orElseThrow();
        assertEquals(userIdTwo, orgTwoUsage.userId());
        assertEquals("OPERATIONS", orgTwoUsage.category());
        assertEquals("Escalated rollout blocker", orgTwoUsage.noteText());
        assertEquals(1, orgTwoUsage.frequencyIncrement());

        verify(checkpointRepository, times(2)).save(any(UpdatePatternAggregationCheckpointEntity.class));
    }

    @Test
    void continuesWhenOneOrgUpsertFails() {
        UUID failingOrgId = UUID.randomUUID();
        UUID succeedingOrgId = UUID.randomUUID();
        UUID failingUserId = UUID.randomUUID();
        UUID succeedingUserId = UUID.randomUUID();

        when(progressEntryRepository.findPatternInputsCreatedSince(WINDOW_START)).thenReturn(List.of(
                input(
                        failingOrgId,
                        failingUserId,
                        CommitCategory.DELIVERY,
                        "Typed note",
                        ProgressNoteSource.USER_TYPED,
                        Instant.parse("2026-03-20T10:00:00Z")
                ),
                input(
                        succeedingOrgId,
                        succeedingUserId,
                        CommitCategory.OPERATIONS,
                        "Other typed note",
                        ProgressNoteSource.USER_TYPED,
                        Instant.parse("2026-03-20T11:00:00Z")
                )
        ));
        when(checkpointRepository.findByOrgId(any())).thenReturn(Optional.empty());
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("boom"))
                .when(userUpdatePatternService)
                .upsertAggregatedPatterns(org.mockito.ArgumentMatchers.argThat(usages ->
                        usages.stream().anyMatch(usage -> usage.orgId().equals(failingOrgId))));

        job.aggregateRecentPatterns();

        verify(userUpdatePatternService, times(2)).upsertAggregatedPatterns(any());
        verify(checkpointRepository, times(1)).save(any(UpdatePatternAggregationCheckpointEntity.class));
    }

    @Test
    void doesNothingWhenNoRecentTypedNotesExist() {
        when(progressEntryRepository.findPatternInputsCreatedSince(WINDOW_START)).thenReturn(List.of(
                input(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        CommitCategory.DELIVERY,
                        "Suggested text",
                        ProgressNoteSource.SUGGESTION_ACCEPTED,
                        Instant.parse("2026-03-20T10:00:00Z")
                )
        ));

        job.aggregateRecentPatterns();

        verify(userUpdatePatternService, never()).upsertAggregatedPatterns(any());
    }

    @Test
    void persistsCheckpointSoLaterRunsDoNotReaggregateSameEntriesAfterRestart() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-03-21T09:00:00Z");
        ProgressEntryPatternInput input = input(
                orgId,
                userId,
                CommitCategory.DELIVERY,
                "Wrapped API integration",
                ProgressNoteSource.USER_TYPED,
                processedAt
        );

        when(progressEntryRepository.findPatternInputsCreatedSince(WINDOW_START)).thenReturn(List.of(input));
        when(checkpointRepository.findByOrgId(orgId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new UpdatePatternAggregationCheckpointEntity(orgId, processedAt)));
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.aggregateRecentPatterns();

        ArgumentCaptor<UpdatePatternAggregationCheckpointEntity> checkpointCaptor =
                ArgumentCaptor.forClass(UpdatePatternAggregationCheckpointEntity.class);
        verify(checkpointRepository).save(checkpointCaptor.capture());
        assertEquals(orgId, checkpointCaptor.getValue().getOrgId());
        assertEquals(processedAt, checkpointCaptor.getValue().getLastAggregatedAt());

        UpdatePatternAggregationJob restartedJob = new UpdatePatternAggregationJob(
                progressEntryRepository,
                userUpdatePatternService,
                checkpointRepository,
                FIXED_CLOCK
        );
        when(checkpointRepository.findAll()).thenReturn(
                List.of(new UpdatePatternAggregationCheckpointEntity(orgId, processedAt))
        );
        when(progressEntryRepository.findPatternInputsCreatedSince(processedAt)).thenReturn(List.of(input));

        restartedJob.aggregateRecentPatterns();

        verify(userUpdatePatternService, times(1)).upsertAggregatedPatterns(any());
    }

    @Test
    void usesEarliestCheckpointAcrossOrgsToLoadRecentInputsOnce() {
        UUID orgIdOne = UUID.randomUUID();
        UUID orgIdTwo = UUID.randomUUID();
        Instant earliestCheckpoint = Instant.parse("2026-03-10T00:00:00Z");
        Instant laterCheckpoint = Instant.parse("2026-03-15T00:00:00Z");

        when(checkpointRepository.findAll()).thenReturn(List.of(
                new UpdatePatternAggregationCheckpointEntity(orgIdOne, laterCheckpoint),
                new UpdatePatternAggregationCheckpointEntity(orgIdTwo, earliestCheckpoint)
        ));
        when(progressEntryRepository.findPatternInputsCreatedSince(earliestCheckpoint)).thenReturn(List.of());

        job.aggregateRecentPatterns();

        verify(progressEntryRepository).findPatternInputsCreatedSince(earliestCheckpoint);
        verify(userUpdatePatternService, never()).upsertAggregatedPatterns(any());
    }

    @Test
    void includesEntriesExactlyOnInitialLookbackBoundary() {
        when(progressEntryRepository.findPatternInputsCreatedSince(WINDOW_START)).thenReturn(List.of(
                input(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        CommitCategory.DELIVERY,
                        "Boundary note",
                        ProgressNoteSource.USER_TYPED,
                        WINDOW_START
                )
        ));
        when(checkpointRepository.findByOrgId(any())).thenReturn(Optional.empty());
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.aggregateRecentPatterns();

        verify(userUpdatePatternService, times(1)).upsertAggregatedPatterns(any());
    }

    @Test
    void scheduledJobRunsNightlyAtTwoAmUtc() throws Exception {
        Method method = UpdatePatternAggregationJob.class.getDeclaredMethod("aggregateRecentPatterns");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 0 2 * * *", scheduled.cron());
        assertEquals("UTC", scheduled.zone());
    }

    @Test
    void jobIsGuardedByConditionalProperty() {
        ConditionalOnProperty conditional =
                UpdatePatternAggregationJob.class.getAnnotation(ConditionalOnProperty.class);

        assertEquals("weekly.usermodel.update-pattern-aggregation.enabled", conditional.name()[0]);
        assertEquals("true", conditional.havingValue());
    }

    private static ProgressEntryPatternInput input(
            UUID orgId,
            UUID userId,
            CommitCategory category,
            String note,
            ProgressNoteSource noteSource,
            Instant createdAt
    ) {
        return new ProgressEntryPatternInput(
                UUID.randomUUID(),
                orgId,
                userId,
                category,
                note,
                noteSource,
                null,
                null,
                createdAt
        );
    }
}
