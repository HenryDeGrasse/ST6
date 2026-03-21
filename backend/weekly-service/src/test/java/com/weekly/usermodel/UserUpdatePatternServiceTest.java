package com.weekly.usermodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link UserUpdatePatternService}.
 */
class UserUpdatePatternServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private UserUpdatePatternRepository repository;
    private UserUpdatePatternService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserUpdatePatternRepository.class);
        service = new UserUpdatePatternService(repository);
    }

    // ─── recordPattern ────────────────────────────────────────────────────────

    @Nested
    class RecordPattern {

        @Test
        void createsNewEntityWhenPatternNotFound() {
            String category = "ON_TRACK";
            String noteText = "Things are going well";

            when(repository.findByOrgIdAndUserIdAndCategoryAndNoteText(
                    ORG_ID, USER_ID, category, noteText))
                    .thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordPattern(ORG_ID, USER_ID, category, noteText);

            ArgumentCaptor<UserUpdatePatternEntity> captor =
                    ArgumentCaptor.forClass(UserUpdatePatternEntity.class);
            verify(repository).save(captor.capture());

            UserUpdatePatternEntity saved = captor.getValue();
            assertNotNull(saved.getId());
            assertEquals(ORG_ID, saved.getOrgId());
            assertEquals(USER_ID, saved.getUserId());
            assertEquals(category, saved.getCategory());
            assertEquals(noteText, saved.getNoteText());
            assertEquals(1, saved.getFrequency());
        }

        @Test
        void incrementsFrequencyOnExistingPattern() {
            String category = "BLOCKED";
            String noteText = "Waiting on approval";

            UserUpdatePatternEntity existing = new UserUpdatePatternEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, category, noteText);
            existing.setFrequency(3);
            Instant originalLastUsedAt = existing.getLastUsedAt();

            when(repository.findByOrgIdAndUserIdAndCategoryAndNoteText(
                    ORG_ID, USER_ID, category, noteText))
                    .thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordPattern(ORG_ID, USER_ID, category, noteText);

            ArgumentCaptor<UserUpdatePatternEntity> captor =
                    ArgumentCaptor.forClass(UserUpdatePatternEntity.class);
            verify(repository).save(captor.capture());

            UserUpdatePatternEntity saved = captor.getValue();
            assertEquals(4, saved.getFrequency());
            assertNotNull(saved.getLastUsedAt());
            assertTrue(
                    !saved.getLastUsedAt().isBefore(originalLastUsedAt),
                    "lastUsedAt should be refreshed to a time >= original");
        }

        @Test
        void handlesNullCategoryGracefully() {
            String noteText = "Generic note without category";

            when(repository.findByOrgIdAndUserIdAndCategoryAndNoteText(
                    ORG_ID, USER_ID, null, noteText))
                    .thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordPattern(ORG_ID, USER_ID, null, noteText);

            ArgumentCaptor<UserUpdatePatternEntity> captor =
                    ArgumentCaptor.forClass(UserUpdatePatternEntity.class);
            verify(repository).save(captor.capture());

            UserUpdatePatternEntity saved = captor.getValue();
            assertEquals(1, saved.getFrequency());
            assertEquals(noteText, saved.getNoteText());
        }
    }

    // ─── getTopPatterns ───────────────────────────────────────────────────────

    @Nested
    class GetTopPatterns {

        @Test
        void delegatesToRepositoryWithCorrectPageRequest() {
            String category = "AT_RISK";
            int limit = 5;

            when(repository.findByOrgIdAndUserIdAndCategoryOrderByFrequencyDesc(
                    eq(ORG_ID), eq(USER_ID), eq(category), eq(PageRequest.of(0, limit))))
                    .thenReturn(List.of());

            service.getTopPatterns(ORG_ID, USER_ID, category, limit);

            verify(repository).findByOrgIdAndUserIdAndCategoryOrderByFrequencyDesc(
                    ORG_ID, USER_ID, category, PageRequest.of(0, limit));
        }

        @Test
        void returnsEmptyListWhenRepositoryReturnsEmpty() {
            String category = "DONE_EARLY";

            when(repository.findByOrgIdAndUserIdAndCategoryOrderByFrequencyDesc(
                    any(), any(), any(), any()))
                    .thenReturn(List.of());

            List<UserUpdatePatternEntity> result =
                    service.getTopPatterns(ORG_ID, USER_ID, category, 5);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
