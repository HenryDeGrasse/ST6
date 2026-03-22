package com.weekly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.weekly.ai.rag.InMemoryEmbeddingClient;
import com.weekly.ai.rag.IssueEmbeddingService;
import com.weekly.ai.rag.IssueEmbeddingTemplate;
import com.weekly.ai.rag.ScoredMatch;
import com.weekly.assignment.domain.AssignmentCompletionStatus;
import com.weekly.assignment.domain.WeeklyAssignmentActualEntity;
import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentActualRepository;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoOutcomeDetail;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit / integration test for {@link IssueEmbeddingService}.
 *
 * <p>Uses a real {@link InMemoryEmbeddingClient} to verify the full embed
 * pipeline without an external vector store. Collaborators are Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
class IssueEmbeddingServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private IssueActivityRepository activityRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private WeeklyAssignmentRepository assignmentRepository;

    @Mock
    private WeeklyAssignmentActualRepository assignmentActualRepository;

    @Mock
    private RcdoClient rcdoClient;

    private InMemoryEmbeddingClient embeddingClient;
    private IssueEmbeddingTemplate template;
    private IssueEmbeddingService service;

    @BeforeEach
    void setUp() {
        embeddingClient = new InMemoryEmbeddingClient();
        template = new IssueEmbeddingTemplate();
        service = new IssueEmbeddingService(
                embeddingClient,
                template,
                issueRepository,
                activityRepository,
                teamRepository,
                assignmentRepository,
                assignmentActualRepository,
                rcdoClient
        );
    }

    @Test
    void embedIssueInsertsVectorIntoStore() {
        UUID issueId = UUID.randomUUID();
        IssueEntity issue = issue(issueId, "PLAT-1", "Add dark mode");
        stubBasicIssue(issue);

        service.embedIssue(issueId);

        assertThat(embeddingClient.size()).isEqualTo(1);

        float[] queryVector = embeddingClient.embed("Issue: PLAT-1 — Add dark mode");
        List<ScoredMatch> results = embeddingClient.query(queryVector, 1, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(issueId.toString());
    }

    @Test
    void embedIssueStoresMetadata() {
        UUID issueId = UUID.randomUUID();
        UUID outcomeId = UUID.randomUUID();
        IssueEntity issue = issue(issueId, "PLAT-2", "Fix auth bug");
        issue.setOutcomeId(outcomeId);
        stubBasicIssue(issue);
        when(rcdoClient.getOutcome(ORG_ID, outcomeId)).thenReturn(Optional.of(
                new RcdoOutcomeDetail(
                        outcomeId.toString(),
                        "Improve authentication reliability",
                        UUID.randomUUID().toString(),
                        "Increase platform trust",
                        UUID.randomUUID().toString(),
                        "Platform resilience"
                )));

        service.embedIssue(issueId);

        float[] q = embeddingClient.embed("Issue: PLAT-2 — Fix auth bug");
        List<ScoredMatch> results = embeddingClient.query(q, 1, null);

        assertThat(results).hasSize(1);
        ScoredMatch match = results.get(0);
        assertThat(match.metadata().get("issueKey")).isEqualTo("PLAT-2");
        assertThat(match.metadata().get("teamId")).isEqualTo(TEAM_ID.toString());
        assertThat(match.metadata().get("orgId")).isEqualTo(ORG_ID.toString());
        assertThat(match.metadata().get("status")).isEqualTo("OPEN");
        assertThat(match.metadata().get("teamName")).isEqualTo("Platform");
        assertThat(match.metadata().get("outcomeId")).isEqualTo(outcomeId.toString());
        assertThat(match.metadata().get("outcomeName")).isEqualTo("Improve authentication reliability");
    }

    @Test
    void embedIssueUsesAssignmentSnapshotOutcomeContextWhenAvailable() {
        UUID issueId = UUID.randomUUID();
        IssueEntity issue = issue(issueId, "PLAT-3", "Ship self-serve billing");
        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), ORG_ID, UUID.randomUUID(), issueId);
        assignment.populateSnapshot(
                UUID.randomUUID(), "Revenue growth",
                UUID.randomUUID(), "Expand self-serve adoption",
                UUID.randomUUID(), "Improve checkout conversion"
        );
        stubBasicIssue(issue);
        when(assignmentRepository.findAllByIssueId(issueId)).thenReturn(List.of(assignment));

        service.embedIssue(issueId);

        float[] q = embeddingClient.embed("Issue: PLAT-3 — Ship self-serve billing");
        List<ScoredMatch> results = embeddingClient.query(q, 1, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata().get("outcomeName"))
                .isEqualTo("Improve checkout conversion");
    }

    @Test
    void embedIssueThrowsWhenIssueNotFound() {
        UUID issueId = UUID.randomUUID();
        when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.embedIssue(issueId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Issue not found");
    }

    @Test
    void deleteEmbeddingRemovesVectorFromStore() {
        UUID issueId = UUID.randomUUID();
        IssueEntity issue = issue(issueId, "PLAT-4", "Remove me");
        stubBasicIssue(issue);

        service.embedIssue(issueId);
        assertThat(embeddingClient.size()).isEqualTo(1);

        service.deleteEmbedding(issueId);
        assertThat(embeddingClient.size()).isEqualTo(0);
    }

    @Test
    void batchEmbedEmbedsAllProvidedIssues() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        mockIssue(id1, "PLAT-5", "Task five");
        mockIssue(id2, "PLAT-6", "Task six");
        mockIssue(id3, "PLAT-7", "Task seven");

        service.batchEmbed(List.of(id1, id2, id3));

        assertThat(embeddingClient.size()).isEqualTo(3);
    }

    @Test
    void batchEmbedContinuesAfterIndividualFailure() {
        UUID goodId = UUID.randomUUID();
        UUID badId = UUID.randomUUID();

        mockIssue(goodId, "PLAT-8", "Good task");
        when(issueRepository.findById(badId)).thenReturn(Optional.empty());

        service.batchEmbed(List.of(goodId, badId));

        assertThat(embeddingClient.size()).isEqualTo(1);
    }

    @Test
    void embeddedIssueCanBeFoundByTeamIdFilter() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        mockIssue(id1, "PLAT-9", "Issue for team A");
        mockIssue(id2, "PLAT-10", "Another issue for team A");

        service.embedIssue(id1);
        service.embedIssue(id2);

        List<ScoredMatch> results = embeddingClient.query(
                embeddingClient.embed("issue"), 10, Map.of("teamId", TEAM_ID.toString()));

        assertThat(results).extracting(ScoredMatch::id)
                .containsExactlyInAnyOrder(id1.toString(), id2.toString());
    }

    @Test
    void embedIssueIncludesReconciliationHistoryInRenderedVectorContent() {
        UUID issueId = UUID.randomUUID();
        IssueEntity issue = issue(issueId, "PLAT-11", "Improve reconciliation context");
        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), ORG_ID, UUID.randomUUID(), issueId);
        ReflectionTestUtils.setField(assignment, "createdAt", java.time.Instant.parse("2026-03-16T10:15:30Z"));
        WeeklyAssignmentActualEntity actual = new WeeklyAssignmentActualEntity(
                assignment.getId(), ORG_ID, AssignmentCompletionStatus.DONE);
        actual.setActualResult("Completed rollout to staging");
        actual.setHoursSpent(new BigDecimal("3.50"));

        stubBasicIssue(issue);
        when(assignmentRepository.findAllByIssueId(issueId)).thenReturn(List.of(assignment));
        when(assignmentActualRepository.findByAssignmentId(assignment.getId()))
                .thenReturn(Optional.of(actual));

        service.embedIssue(issueId);

        List<ScoredMatch> results = embeddingClient.query(
                embeddingClient.embed("Completed rollout to staging"), 1, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(issueId.toString());
    }

    private IssueEntity issue(UUID id, String key, String title) {
        int seq = Integer.parseInt(key.split("-")[1]);
        IssueEntity entity = new IssueEntity(id, ORG_ID, TEAM_ID, key, seq, title, CREATOR_ID);
        entity.setStatus(IssueStatus.OPEN);
        return entity;
    }

    private void mockIssue(UUID id, String key, String title) {
        stubBasicIssue(issue(id, key, title));
    }

    private void stubBasicIssue(IssueEntity issue) {
        when(issueRepository.findById(issue.getId())).thenReturn(Optional.of(issue));
        when(activityRepository.findAllByIssueIdOrderByCreatedAtAsc(issue.getId())).thenReturn(List.of());
        when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID))
                .thenReturn(Optional.of(new TeamEntity(TEAM_ID, ORG_ID, "Platform", "PLAT", CREATOR_ID)));
        when(assignmentRepository.findAllByIssueId(issue.getId())).thenReturn(List.of());
    }
}
