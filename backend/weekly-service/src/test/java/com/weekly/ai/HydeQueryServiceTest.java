package com.weekly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.weekly.ai.rag.HydeQueryService;
import com.weekly.ai.rag.InMemoryEmbeddingClient;
import com.weekly.ai.rag.OutcomeRiskContext;
import com.weekly.ai.rag.UserWorkContext;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

/**
 * Unit + integration tests for {@link HydeQueryService}.
 *
 * <p>Uses {@link InMemoryEmbeddingClient} and {@link StubLlmClient} to verify
 * the full HyDE pipeline without external network calls.
 */
@ExtendWith(MockitoExtension.class)
class HydeQueryServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);

    @Mock
    private IssueRepository issueRepository;

    private InMemoryEmbeddingClient embeddingClient;
    private StubLlmClient llmClient;
    private HydeQueryService hydeQueryService;

    @BeforeEach
    void setUp() {
        embeddingClient = new InMemoryEmbeddingClient();
        llmClient = new StubLlmClient();
        hydeQueryService = new HydeQueryService(llmClient, embeddingClient, issueRepository);
    }

    // ── recommendWithHyde ─────────────────────────────────────────────────────

    @Nested
    class RecommendWithHyde {

        @Test
        void returnsEmptyListWhenNoIssuesEmbedded() {
            UserWorkContext ctx = buildUserContext();
            OutcomeRiskContext risk = emptyRisk();

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.recommendWithHyde(ctx, risk, 5);

            // In-memory store is empty → no matches
            assertThat(results).isEmpty();
        }

        @Test
        void returnsMatchingIssuesAfterEmbedding() {
            // Seed the in-memory store with two issues
            UUID issueId1 = UUID.randomUUID();
            UUID issueId2 = UUID.randomUUID();
            embedIssue(issueId1, "Implement caching layer for API responses",
                    IssueStatus.OPEN.name());
            embedIssue(issueId2, "Fix authentication bug in login flow",
                    IssueStatus.OPEN.name());

            UserWorkContext ctx = buildUserContext();
            List<HydeQueryService.IssueId> results =
                    hydeQueryService.recommendWithHyde(ctx, emptyRisk(), 5);

            // The stub LLM generates a caching-related hypothetical → issueId1 should score higher
            assertThat(results).isNotEmpty();
            assertThat(results).anyMatch(r -> r.issueId().equals(issueId1));
        }

        @Test
        void respectsOrgFilter() {
            UUID foreignOrgId = UUID.randomUUID();
            UUID issueIdSameOrg = UUID.randomUUID();
            UUID issueIdOtherOrg = UUID.randomUUID();

            // Same org — should appear in results
            embeddingClient.upsert(issueIdSameOrg.toString(),
                    embeddingClient.embed("Same-org issue"),
                    Map.of("orgId", ORG_ID.toString(), "status", IssueStatus.OPEN.name()));

            // Other org — should NOT appear
            embeddingClient.upsert(issueIdOtherOrg.toString(),
                    embeddingClient.embed("Foreign-org issue"),
                    Map.of("orgId", foreignOrgId.toString(), "status", IssueStatus.OPEN.name()));

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.recommendWithHyde(buildUserContext(), emptyRisk(), 10);

            List<String> resultIds = results.stream()
                    .map(r -> r.issueId().toString()).toList();
            assertThat(resultIds).contains(issueIdSameOrg.toString());
            assertThat(resultIds).doesNotContain(issueIdOtherOrg.toString());
        }

        @Test
        void respectsPermittedTeamIds() {
            UUID teamA = UUID.randomUUID();
            UUID teamB = UUID.randomUUID();
            UUID issueIdTeamA = UUID.randomUUID();
            UUID issueIdTeamB = UUID.randomUUID();

            embeddingClient.upsert(issueIdTeamA.toString(),
                    embeddingClient.embed("Caching work for team A"),
                    Map.of(
                            "orgId", ORG_ID.toString(),
                            "status", IssueStatus.OPEN.name(),
                            "teamId", teamA.toString()));
            embeddingClient.upsert(issueIdTeamB.toString(),
                    embeddingClient.embed("Caching work for team B"),
                    Map.of(
                            "orgId", ORG_ID.toString(),
                            "status", IssueStatus.OPEN.name(),
                            "teamId", teamB.toString()));

            UserWorkContext restrictedContext = new UserWorkContext(
                    USER_ID, ORG_ID, WEEK_START, 40.0, 0.0,
                    List.of(), List.of(), List.of(), List.of(teamA)
            );

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.recommendWithHyde(restrictedContext, emptyRisk(), 10);

            List<String> resultIds = results.stream().map(r -> r.issueId().toString()).toList();
            assertThat(resultIds).contains(issueIdTeamA.toString());
            assertThat(resultIds).doesNotContain(issueIdTeamB.toString());
        }

        @Test
        void respectsStatusFilter() {
            UUID openIssueId = UUID.randomUUID();
            UUID doneIssueId = UUID.randomUUID();

            embeddingClient.upsert(openIssueId.toString(),
                    embeddingClient.embed("Open issue text"),
                    Map.of("orgId", ORG_ID.toString(), "status", IssueStatus.OPEN.name()));

            embeddingClient.upsert(doneIssueId.toString(),
                    embeddingClient.embed("Done issue text"),
                    Map.of("orgId", ORG_ID.toString(), "status", IssueStatus.DONE.name()));

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.recommendWithHyde(buildUserContext(), emptyRisk(), 10);

            List<String> resultIds = results.stream()
                    .map(r -> r.issueId().toString()).toList();
            assertThat(resultIds).contains(openIssueId.toString());
            assertThat(resultIds).doesNotContain(doneIssueId.toString());
        }

        @Test
        void fallsBackToDatabaseOnLlmUnavailable() {
            llmClient.setAvailable(false);
            UUID fallbackIssueId = UUID.randomUUID();
            IssueEntity fallbackIssue = mockIssue(fallbackIssueId, "Fallback issue");

            when(issueRepository.findAllByOrgIdAndStatus(
                    eq(ORG_ID), eq(IssueStatus.OPEN), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(fallbackIssue)));

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.recommendWithHyde(buildUserContext(), emptyRisk(), 5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).issueId()).isEqualTo(fallbackIssueId);
            assertThat(results.get(0).score()).isEqualTo(0.0f);
        }
    }

    // ── searchWithHyde ────────────────────────────────────────────────────────

    @Nested
    class SearchWithHyde {

        @Test
        void returnsEmptyListWhenStoreIsEmpty() {
            List<HydeQueryService.IssueId> results =
                    hydeQueryService.searchWithHyde(ORG_ID, "caching", 5, null);
            assertThat(results).isEmpty();
        }

        @Test
        void returnsScoredMatchesForQuery() {
            UUID issueId = UUID.randomUUID();
            embedIssue(issueId, "Implement Redis caching for API responses",
                    IssueStatus.OPEN.name());

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.searchWithHyde(ORG_ID, "caching API", 5, null);

            assertThat(results).isNotEmpty();
        }

        @Test
        void appliesAdditionalFilters() {
            UUID issueId1 = UUID.randomUUID();
            UUID issueId2 = UUID.randomUUID();
            UUID teamA = UUID.randomUUID();
            UUID teamB = UUID.randomUUID();

            embeddingClient.upsert(issueId1.toString(),
                    embeddingClient.embed("Issue for team A"),
                    Map.of("orgId", ORG_ID.toString(), "teamId", teamA.toString()));
            embeddingClient.upsert(issueId2.toString(),
                    embeddingClient.embed("Issue for team B"),
                    Map.of("orgId", ORG_ID.toString(), "teamId", teamB.toString()));

            Map<String, Object> filter = Map.of("teamId", teamA.toString());
            List<HydeQueryService.IssueId> results =
                    hydeQueryService.searchWithHyde(ORG_ID, "team work", 10, filter);

            List<String> ids = results.stream().map(r -> r.issueId().toString()).toList();
            assertThat(ids).contains(issueId1.toString());
            assertThat(ids).doesNotContain(issueId2.toString());
        }

        @Test
        void fallsBackOnLlmUnavailable() {
            llmClient.setAvailable(false);
            UUID fallbackIssueId = UUID.randomUUID();
            IssueEntity fallbackIssue = mockIssue(fallbackIssueId, "Fallback search issue");

            when(issueRepository.findAllByOrgIdAndStatusNot(
                    eq(ORG_ID), eq(IssueStatus.ARCHIVED), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(fallbackIssue)));

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.searchWithHyde(ORG_ID, "caching", 5, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).score()).isEqualTo(0.0f);
        }
    }

    // ── findSimilar ───────────────────────────────────────────────────────────

    @Nested
    class FindSimilar {

        @Test
        void excludesSourceIssueFromResults() {
            UUID sourceId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            String sourceText = "Implement caching layer";

            IssueEntity sourceIssue = mockIssue(sourceId, sourceText);
            when(issueRepository.findByOrgIdAndId(ORG_ID, sourceId)).thenReturn(Optional.of(sourceIssue));

            // Embed both — source will be closest to itself, but should be excluded
            embeddingClient.upsert(sourceId.toString(),
                    embeddingClient.embed(sourceText),
                    Map.of("orgId", ORG_ID.toString()));
            embeddingClient.upsert(otherId.toString(),
                    embeddingClient.embed("Build Redis caching infrastructure"),
                    Map.of("orgId", ORG_ID.toString()));

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.findSimilar(ORG_ID, sourceId, 5);

            List<String> ids = results.stream().map(r -> r.issueId().toString()).toList();
            assertThat(ids).doesNotContain(sourceId.toString());
            assertThat(ids).contains(otherId.toString());
        }

        @Test
        void returnsEmptyOnIssueNotFound() {
            UUID missingId = UUID.randomUUID();
            when(issueRepository.findByOrgIdAndId(ORG_ID, missingId)).thenReturn(Optional.empty());

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.findSimilar(ORG_ID, missingId, 5);

            // Falls back to DB query (which returns nothing)
            assertThat(results).isEmpty();
        }

        @Test
        void returnsTopKResults() {
            UUID sourceId = UUID.randomUUID();
            IssueEntity sourceIssue = mockIssue(sourceId, "Source issue");
            when(issueRepository.findByOrgIdAndId(ORG_ID, sourceId)).thenReturn(Optional.of(sourceIssue));

            // Embed source + 5 others
            embeddingClient.upsert(sourceId.toString(),
                    embeddingClient.embed("Source issue"),
                    Map.of("orgId", ORG_ID.toString()));
            for (int i = 0; i < 5; i++) {
                embeddingClient.upsert(UUID.randomUUID().toString(),
                        embeddingClient.embed("Similar issue " + i),
                        Map.of("orgId", ORG_ID.toString()));
            }

            List<HydeQueryService.IssueId> results =
                    hydeQueryService.findSimilar(ORG_ID, sourceId, 3);

            assertThat(results).hasSize(3);
        }
    }

    // ── IssueId value type ────────────────────────────────────────────────────

    @Test
    void issueIdRecordHoldsValues() {
        UUID id = UUID.randomUUID();
        HydeQueryService.IssueId issueId = new HydeQueryService.IssueId(id, 0.9f);
        assertThat(issueId.issueId()).isEqualTo(id);
        assertThat(issueId.score()).isEqualTo(0.9f);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void embedIssue(UUID issueId, String text, String status) {
        float[] vector = embeddingClient.embed(text);
        embeddingClient.upsert(issueId.toString(), vector,
                Map.of("orgId", ORG_ID.toString(), "status", status));
    }

    private IssueEntity mockIssue(UUID issueId, String title) {
        UUID teamId = UUID.randomUUID();
        IssueEntity issue = new IssueEntity(
                issueId, ORG_ID, teamId,
                "TEST-" + (int) (Math.random() * 1000),
                1, title, USER_ID
        );
        return issue;
    }

    private UserWorkContext buildUserContext() {
        return new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 0.0, List.of(), List.of(), List.of()
        );
    }

    private OutcomeRiskContext emptyRisk() {
        return new OutcomeRiskContext(List.of(), List.of());
    }
}
