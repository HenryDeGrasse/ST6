package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.dto.CheckInEntryResponse;
import com.weekly.plan.dto.CheckInHistoryResponse;
import com.weekly.plan.dto.CheckInRequest;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.shared.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link DefaultCheckInService}: append check-in and retrieve history.
 */
class CheckInServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    private WeeklyCommitRepository commitRepository;
    private ProgressEntryRepository progressEntryRepository;
    private CheckInService checkInService;

    @BeforeEach
    void setUp() {
        commitRepository = mock(WeeklyCommitRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        checkInService = new DefaultCheckInService(commitRepository, progressEntryRepository);
    }

    private WeeklyCommitEntity makeCommit(UUID commitId, UUID planId) {
        return new WeeklyCommitEntity(commitId, ORG_ID, planId, "Test commit");
    }

    // ─── addCheckIn ───────────────────────────────────────────────────────────

    @Nested
    class AddCheckIn {

        @Test
        void savesEntryWithCorrectFields() {
            UUID commitId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, planId);
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CheckInRequest request = new CheckInRequest("ON_TRACK", "Going well");
            CheckInEntryResponse response = checkInService.addCheckIn(ORG_ID, commitId, request);

            assertNotNull(response.id());
            assertEquals(commitId.toString(), response.commitId());
            assertEquals("ON_TRACK", response.status());
            assertEquals("Going well", response.note());
            assertNotNull(response.createdAt());
        }

        @Test
        void savesEntryWithNullNoteAsEmptyString() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CheckInRequest request = new CheckInRequest("AT_RISK", null);
            CheckInEntryResponse response = checkInService.addCheckIn(ORG_ID, commitId, request);

            assertEquals("", response.note());
        }

        @Test
        void savesEntryAndPersistsToRepository() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CheckInRequest request = new CheckInRequest("BLOCKED", "Waiting on dependency");
            checkInService.addCheckIn(ORG_ID, commitId, request);

            ArgumentCaptor<ProgressEntryEntity> captor =
                    ArgumentCaptor.forClass(ProgressEntryEntity.class);
            verify(progressEntryRepository).save(captor.capture());

            ProgressEntryEntity saved = captor.getValue();
            assertNotNull(saved.getId());
            assertEquals(ORG_ID, saved.getOrgId());
            assertEquals(commitId, saved.getCommitId());
            assertEquals(ProgressStatus.BLOCKED, saved.getStatus());
            assertEquals("Waiting on dependency", saved.getNote());
        }

        @Test
        void acceptsAllValidStatusValues() {
            UUID planId = UUID.randomUUID();
            for (String status : List.of("ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY")) {
                UUID commitId = UUID.randomUUID();
                WeeklyCommitEntity commit = makeCommit(commitId, planId);
                when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
                when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                CheckInRequest request = new CheckInRequest(status, "");
                CheckInEntryResponse response = checkInService.addCheckIn(ORG_ID, commitId, request);

                assertEquals(status, response.status());
            }
        }

        @Test
        void throwsCommitNotFoundWhenCommitMissing() {
            UUID commitId = UUID.randomUUID();
            when(commitRepository.findById(commitId)).thenReturn(Optional.empty());

            CheckInRequest request = new CheckInRequest("ON_TRACK", null);

            assertThrows(CommitNotFoundException.class, () ->
                    checkInService.addCheckIn(ORG_ID, commitId, request));

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsCommitNotFoundWhenOrgMismatch() {
            UUID commitId = UUID.randomUUID();
            UUID differentOrg = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            // commit belongs to ORG_ID, but we use differentOrg
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            CheckInRequest request = new CheckInRequest("ON_TRACK", null);

            assertThrows(CommitNotFoundException.class, () ->
                    checkInService.addCheckIn(differentOrg, commitId, request));

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsValidationExceptionForInvalidStatus() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            CheckInRequest request = new CheckInRequest("INVALID_STATUS", "note");

            PlanValidationException ex = assertThrows(PlanValidationException.class, () ->
                    checkInService.addCheckIn(ORG_ID, commitId, request));

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsValidationExceptionForNullStatus() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            CheckInRequest request = new CheckInRequest(null, "note");

            PlanValidationException ex = assertThrows(PlanValidationException.class, () ->
                    checkInService.addCheckIn(ORG_ID, commitId, request));

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void throwsValidationExceptionForBlankStatus() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            CheckInRequest request = new CheckInRequest("   ", "note");

            PlanValidationException ex = assertThrows(PlanValidationException.class, () ->
                    checkInService.addCheckIn(ORG_ID, commitId, request));

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
            verify(progressEntryRepository, never()).save(any());
        }
    }

    // ─── getHistory ───────────────────────────────────────────────────────────

    @Nested
    class GetHistory {

        @Test
        void returnsEmptyHistoryWhenNoEntries() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(progressEntryRepository.findByOrgIdAndCommitIdOrderByCreatedAtAsc(ORG_ID, commitId))
                    .thenReturn(List.of());

            CheckInHistoryResponse response = checkInService.getHistory(ORG_ID, commitId);

            assertEquals(commitId.toString(), response.commitId());
            assertEquals(0, response.entries().size());
        }

        @Test
        void returnsEntriesInOrder() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            ProgressEntryEntity e1 = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId, ProgressStatus.ON_TRACK, "first");
            ProgressEntryEntity e2 = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId, ProgressStatus.AT_RISK, "second");

            when(progressEntryRepository.findByOrgIdAndCommitIdOrderByCreatedAtAsc(ORG_ID, commitId))
                    .thenReturn(List.of(e1, e2));

            CheckInHistoryResponse response = checkInService.getHistory(ORG_ID, commitId);

            assertEquals(2, response.entries().size());
            assertEquals("ON_TRACK", response.entries().get(0).status());
            assertEquals("first", response.entries().get(0).note());
            assertEquals("AT_RISK", response.entries().get(1).status());
            assertEquals("second", response.entries().get(1).note());
        }

        @Test
        void throwsCommitNotFoundWhenCommitMissing() {
            UUID commitId = UUID.randomUUID();
            when(commitRepository.findById(commitId)).thenReturn(Optional.empty());

            assertThrows(CommitNotFoundException.class, () ->
                    checkInService.getHistory(ORG_ID, commitId));
        }

        @Test
        void throwsCommitNotFoundWhenOrgMismatch() {
            UUID commitId = UUID.randomUUID();
            UUID differentOrg = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            assertThrows(CommitNotFoundException.class, () ->
                    checkInService.getHistory(differentOrg, commitId));
        }

        @Test
        void includesAllEntryFieldsInResponse() {
            UUID commitId = UUID.randomUUID();
            UUID entryId = UUID.randomUUID();
            WeeklyCommitEntity commit = makeCommit(commitId, UUID.randomUUID());
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));

            ProgressEntryEntity entry = new ProgressEntryEntity(
                    entryId, ORG_ID, commitId, ProgressStatus.DONE_EARLY, "Finished ahead of schedule");

            when(progressEntryRepository.findByOrgIdAndCommitIdOrderByCreatedAtAsc(ORG_ID, commitId))
                    .thenReturn(List.of(entry));

            CheckInHistoryResponse response = checkInService.getHistory(ORG_ID, commitId);

            CheckInEntryResponse e = response.entries().get(0);
            assertEquals(entryId.toString(), e.id());
            assertEquals(commitId.toString(), e.commitId());
            assertEquals("DONE_EARLY", e.status());
            assertEquals("Finished ahead of schedule", e.note());
            assertNotNull(e.createdAt());
        }
    }
}
