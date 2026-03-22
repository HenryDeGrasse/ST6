package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.ai.rag.EmbeddingClient;
import com.weekly.ai.rag.ScoredMatch;
import java.util.Map;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.NextWorkDataProvider;
import com.weekly.shared.NextWorkDataProvider.RcdoCoverageGap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CoverageGapInspirationService}.
 */
class CoverageGapInspirationServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);

    private NextWorkDataProvider nextWorkDataProvider;
    private IssueRepository issueRepository;
    private EmbeddingClient embeddingClient;
    private CoverageGapInspirationService service;

    @BeforeEach
    void setUp() {
        nextWorkDataProvider = mock(NextWorkDataProvider.class);
        issueRepository = mock(IssueRepository.class);
        embeddingClient = mock(EmbeddingClient.class);
        service = new CoverageGapInspirationService(
                nextWorkDataProvider, issueRepository, embeddingClient);
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    private static RcdoCoverageGap gap(String outcomeName, int weeksMissing, int prevCommits) {
        return new RcdoCoverageGap(
                UUID.randomUUID().toString(),
                outcomeName,
                "Parent Objective",
                "Ship Better Software",
                weeksMissing,
                prevCommits
        );
    }

    private static IssueEntity doneIssue(UUID issueId, UUID orgId, BigDecimal estimatedHours) {
        IssueEntity issue = new IssueEntity(
                issueId, orgId, UUID.randomUUID(),
                "DONE-" + issueId.toString().substring(0, 4),
                1, "Done issue", UUID.randomUUID());
        issue.setEstimatedHours(estimatedHours);
        issue.setStatus(IssueStatus.DONE);
        return issue;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    class WhenNoGapsFound {
        @Test
        void returnsEmptyInspirations() {
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertTrue(result.inspirations().isEmpty());
        }
    }

    @Nested
    class WhenGapsFound {

        @Test
        void returnsSuggestionsForEachGap() {
            RcdoCoverageGap gap1 = gap("Improve Security Posture", 3, 5);
            RcdoCoverageGap gap2 = gap("Reduce Latency", 2, 8);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap1, gap2));
            // No RAG matches
            when(embeddingClient.embed(any())).thenThrow(new RuntimeException("Embedding unavailable"));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(2, result.inspirations().size());
        }

        @Test
        void suggestedTitleIncludesOutcomeName() {
            RcdoCoverageGap gap = gap("Reduce Deploy Risk", 4, 3);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(embeddingClient.embed(any())).thenThrow(new RuntimeException("unavailable"));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            assertEquals(1, result.inspirations().size());
            CoverageGapInspirationService.InspirationSuggestion s = result.inspirations().get(0);
            assertTrue(s.suggestedTitle().contains("Reduce Deploy Risk"),
                    "Title should reference the outcome name");
        }

        @Test
        void rationaleIncludesWeeksMissingAndPreviousCommits() {
            RcdoCoverageGap gap = gap("Improve Observability", 3, 7);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(embeddingClient.embed(any())).thenThrow(new RuntimeException("unavailable"));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            String rationale = result.inspirations().get(0).rationale();
            assertTrue(rationale.contains("3"), "Rationale should mention weeks missing");
            assertTrue(rationale.contains("7"), "Rationale should mention previous commit count");
        }

        @Test
        void weeksMissingFieldMatchesGap() {
            RcdoCoverageGap gap = gap("Scale Platform", 4, 2);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(embeddingClient.embed(any())).thenThrow(new RuntimeException("unavailable"));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            assertEquals(4, result.inspirations().get(0).weeksMissing());
        }
    }

    @Nested
    class EffortEstimation {

        @Test
        void usesDefaultWhenRagUnavailable() {
            RcdoCoverageGap gap = gap("Improve Reliability", 2, 4);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(embeddingClient.embed(any())).thenThrow(new RuntimeException("Embedding unavailable"));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            BigDecimal estimated = result.inspirations().get(0).estimatedHours();
            assertEquals(BigDecimal.valueOf(CoverageGapInspirationService.DEFAULT_EFFORT_HOURS),
                    estimated);
        }

        @Test
        void usesDefaultWhenNoSimilarDoneIssuesFound() {
            RcdoCoverageGap gap = gap("New Feature Area", 3, 2);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f, 0.2f});
            when(embeddingClient.query(any(), anyInt(), any())).thenReturn(List.of());

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            BigDecimal estimated = result.inspirations().get(0).estimatedHours();
            assertEquals(BigDecimal.valueOf(CoverageGapInspirationService.DEFAULT_EFFORT_HOURS),
                    estimated);
        }

        @Test
        void usesMedianOfSimilarDoneIssuesForEffortEstimate() {
            RcdoCoverageGap gap = gap("Improve Performance", 3, 5);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));

            UUID issueId1 = UUID.randomUUID();
            UUID issueId2 = UUID.randomUUID();
            UUID issueId3 = UUID.randomUUID();

            when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f});
            when(embeddingClient.query(any(), anyInt(), any())).thenReturn(List.of(
                    new ScoredMatch(issueId1.toString(), 0.95f, Map.of()),
                    new ScoredMatch(issueId2.toString(), 0.90f, Map.of()),
                    new ScoredMatch(issueId3.toString(), 0.85f, Map.of())
            ));
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId1))
                    .thenReturn(Optional.of(doneIssue(issueId1, ORG_ID, BigDecimal.valueOf(4.0))));
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId2))
                    .thenReturn(Optional.of(doneIssue(issueId2, ORG_ID, BigDecimal.valueOf(8.0))));
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId3))
                    .thenReturn(Optional.of(doneIssue(issueId3, ORG_ID, BigDecimal.valueOf(6.0))));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            BigDecimal estimated = result.inspirations().get(0).estimatedHours();
            // Sorted: [4.0, 6.0, 8.0] → median = 6.0
            assertEquals(new BigDecimal("6.0"), estimated);
        }

        @Test
        void ignoresMatchedIssuesThatAreNoLongerDone() {
            RcdoCoverageGap gap = gap("Improve Performance", 3, 5);
            when(nextWorkDataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));

            UUID doneIssueId = UUID.randomUUID();
            UUID openIssueId = UUID.randomUUID();

            when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f});
            when(embeddingClient.query(any(), anyInt(), any())).thenReturn(List.of(
                    new ScoredMatch(doneIssueId.toString(), 0.95f, Map.of()),
                    new ScoredMatch(openIssueId.toString(), 0.90f, Map.of())
            ));
            when(issueRepository.findByOrgIdAndId(ORG_ID, doneIssueId))
                    .thenReturn(Optional.of(doneIssue(doneIssueId, ORG_ID, BigDecimal.valueOf(6.0))));
            IssueEntity openIssue = doneIssue(openIssueId, ORG_ID, BigDecimal.valueOf(20.0));
            openIssue.setStatus(IssueStatus.OPEN);
            when(issueRepository.findByOrgIdAndId(ORG_ID, openIssueId))
                    .thenReturn(Optional.of(openIssue));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            assertEquals(new BigDecimal("6.0"), result.inspirations().get(0).estimatedHours());
        }
    }

    @Nested
    class ComputeMedianTests {

        @Test
        void oddNumberOfValues() {
            List<BigDecimal> values = List.of(
                    BigDecimal.valueOf(4), BigDecimal.valueOf(8), BigDecimal.valueOf(6));
            assertEquals(new BigDecimal("6.0"),
                    CoverageGapInspirationService.computeMedian(values));
        }

        @Test
        void evenNumberOfValues() {
            List<BigDecimal> values = List.of(
                    BigDecimal.valueOf(4), BigDecimal.valueOf(8));
            assertEquals(new BigDecimal("6.0"),
                    CoverageGapInspirationService.computeMedian(values));
        }

        @Test
        void singleValue() {
            List<BigDecimal> values = List.of(BigDecimal.valueOf(5));
            assertEquals(new BigDecimal("5.0"),
                    CoverageGapInspirationService.computeMedian(values));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void returnsUnavailableOnDataProviderException() {
            when(nextWorkDataProvider.getTeamCoverageGaps(any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("DB failure"));

            CoverageGapInspirationService.InspirationResult result =
                    service.generateInspirations(ORG_ID, WEEK_START);

            assertEquals("unavailable", result.status());
        }
    }
}
