package com.weekly.usermodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link TeamPatternService}.
 */
class TeamPatternServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    private UserUpdatePatternRepository repository;
    private TeamPatternService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserUpdatePatternRepository.class);
        service = new TeamPatternService(repository);
    }

    @Nested
    class GetTopPatterns {

        @Test
        void delegatesToRepositoryWithRequestedLimit() {
            when(repository.findTopTeamPatternsByOrgIdAndCategory(
                    eq(ORG_ID), eq("DELIVERY"), eq(PageRequest.of(0, 3))))
                    .thenReturn(List.of());

            service.getTopPatterns(ORG_ID, "DELIVERY", 3);

            verify(repository).findTopTeamPatternsByOrgIdAndCategory(
                    ORG_ID, "DELIVERY", PageRequest.of(0, 3));
        }

        @Test
        void returnsOnlyRolledUpNoteTextsInOrder() {
            when(repository.findTopTeamPatternsByOrgIdAndCategory(
                    eq(ORG_ID), eq("DELIVERY"), eq(PageRequest.of(0, 3))))
                    .thenReturn(List.of(
                            new TeamPatternRollup(
                                    "Shared rollout update",
                                    8,
                                    Instant.parse("2026-03-21T09:00:00Z")
                            ),
                            new TeamPatternRollup(
                                    "Wrapped API integration",
                                    5,
                                    Instant.parse("2026-03-20T09:00:00Z")
                            )
                    ));

            List<String> result = service.getTopPatterns(ORG_ID, "DELIVERY", 3);

            assertEquals(List.of("Shared rollout update", "Wrapped API integration"), result);
        }

        @Test
        void filtersBlankNoteTextsDefensively() {
            when(repository.findTopTeamPatternsByOrgIdAndCategory(
                    eq(ORG_ID), eq("DELIVERY"), eq(PageRequest.of(0, 5))))
                    .thenReturn(List.of(
                            new TeamPatternRollup("  ", 9, Instant.parse("2026-03-21T09:00:00Z")),
                            new TeamPatternRollup("Reliable fallback", 4, Instant.parse("2026-03-20T09:00:00Z")),
                            new TeamPatternRollup(null, 2, Instant.parse("2026-03-19T09:00:00Z"))
                    ));

            List<String> result = service.getTopPatterns(ORG_ID, "DELIVERY", 5);

            assertEquals(List.of("Reliable fallback"), result);
        }

        @Test
        void returnsEmptyListForNonPositiveLimitWithoutQuerying() {
            List<String> result = service.getTopPatterns(ORG_ID, "DELIVERY", 0);

            assertTrue(result.isEmpty());
        }

        @Test
        void supportsNullCategoryFallback() {
            when(repository.findTopTeamPatternsByOrgIdAndCategory(
                    eq(ORG_ID), eq(null), eq(PageRequest.of(0, 2))))
                    .thenReturn(List.of(
                            new TeamPatternRollup(
                                    "General status update",
                                    3,
                                    Instant.parse("2026-03-21T09:00:00Z")
                            )
                    ));

            List<String> result = service.getTopPatterns(ORG_ID, null, 2);

            assertEquals(List.of("General status update"), result);
        }
    }
}
