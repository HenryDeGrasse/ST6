package com.weekly.ai.rag;

import com.weekly.assignment.domain.WeeklyAssignmentActualEntity;
import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentActualRepository;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoOutcomeDetail;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamRepository;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that embeds issues into the vector store (Phase 6, Step 11).
 *
 * <p>Each embed operation:
 * <ol>
 *   <li>Loads the {@link IssueEntity}, related team/outcome context, issue activity log,
 *       and recent assignment actuals.</li>
 *   <li>Renders structured text via {@link IssueEmbeddingTemplate}.</li>
 *   <li>Calls {@link EmbeddingClient#embed(String)} to obtain a 1536-dim vector.</li>
 *   <li>Upserts the vector into the store with metadata for metadata-filtered queries.</li>
 * </ol>
 */
@Service
public class IssueEmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(IssueEmbeddingService.class);

    private final EmbeddingClient embeddingClient;
    private final IssueEmbeddingTemplate template;
    private final IssueRepository issueRepository;
    private final IssueActivityRepository activityRepository;
    private final TeamRepository teamRepository;
    private final WeeklyAssignmentRepository assignmentRepository;
    private final WeeklyAssignmentActualRepository assignmentActualRepository;
    private final RcdoClient rcdoClient;

    public IssueEmbeddingService(
            EmbeddingClient embeddingClient,
            IssueEmbeddingTemplate template,
            IssueRepository issueRepository,
            IssueActivityRepository activityRepository,
            TeamRepository teamRepository,
            WeeklyAssignmentRepository assignmentRepository,
            WeeklyAssignmentActualRepository assignmentActualRepository,
            RcdoClient rcdoClient
    ) {
        this.embeddingClient = embeddingClient;
        this.template = template;
        this.issueRepository = issueRepository;
        this.activityRepository = activityRepository;
        this.teamRepository = teamRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentActualRepository = assignmentActualRepository;
        this.rcdoClient = rcdoClient;
    }

    /**
     * Embeds a single issue and upserts its vector into the vector store.
     *
     * @param issueId the UUID of the issue to embed
     * @throws IllegalArgumentException if no issue with the given ID exists
     */
    @Transactional(readOnly = true)
    public void embedIssue(UUID issueId) {
        IssueEntity issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));
        List<IssueActivityEntity> activities =
                activityRepository.findAllByIssueIdOrderByCreatedAtAsc(issueId);
        List<WeeklyAssignmentEntity> assignments = assignmentRepository.findAllByIssueId(issueId);

        String teamName = resolveTeamName(issue);
        IssueEmbeddingTemplate.OutcomeContext outcomeContext = resolveOutcomeContext(issue, assignments);
        List<IssueEmbeddingTemplate.ReconciliationHistoryEntry> reconciliationHistory =
                buildReconciliationHistory(assignments);

        String text = template.render(issue, teamName, outcomeContext, activities, reconciliationHistory);
        float[] vector = embeddingClient.embed(text);
        Map<String, Object> metadata = buildMetadata(issue, teamName, outcomeContext);

        embeddingClient.upsert(issueId.toString(), vector, metadata);
        LOG.debug("Embedded issue {} (key={})", issueId, issue.getIssueKey());
    }

    /**
     * Removes the embedding for {@code issueId} from the vector store.
     *
     * @param issueId the UUID of the issue whose embedding should be removed
     */
    public void deleteEmbedding(UUID issueId) {
        embeddingClient.delete(issueId.toString());
        LOG.debug("Deleted embedding for issue {}", issueId);
    }

    /**
     * Embeds multiple issues in sequence (for backfill operations).
     *
     * <p>Failures on individual issues are logged and skipped — the batch
     * continues processing remaining IDs.
     *
     * @param issueIds list of issue UUIDs to embed
     */
    @Transactional(readOnly = true)
    public void batchEmbed(List<UUID> issueIds) {
        LOG.info("Starting batch embed for {} issues", issueIds.size());
        int success = 0;
        for (UUID issueId : issueIds) {
            try {
                embedIssue(issueId);
                success++;
            } catch (Exception e) {
                LOG.error("Failed to embed issue {}: {}", issueId, e.getMessage());
            }
        }
        LOG.info("Batch embed complete: {}/{} succeeded", success, issueIds.size());
    }

    private String resolveTeamName(IssueEntity issue) {
        return teamRepository.findByOrgIdAndId(issue.getOrgId(), issue.getTeamId())
                .map(TeamEntity::getName)
                .orElse(null);
    }

    private IssueEmbeddingTemplate.OutcomeContext resolveOutcomeContext(
            IssueEntity issue,
            List<WeeklyAssignmentEntity> assignments
    ) {
        WeeklyAssignmentEntity snapshotAssignment = assignments.stream()
                .filter(this::hasSnapshotContext)
                .sorted(Comparator.comparing(WeeklyAssignmentEntity::getCreatedAt).reversed())
                .findFirst()
                .orElse(null);
        if (snapshotAssignment != null) {
            return new IssueEmbeddingTemplate.OutcomeContext(
                    snapshotAssignment.getSnapshotRallyCryName(),
                    snapshotAssignment.getSnapshotObjectiveName(),
                    snapshotAssignment.getSnapshotOutcomeName()
            );
        }

        if (issue.getOutcomeId() == null) {
            return null;
        }

        try {
            return rcdoClient.getOutcome(issue.getOrgId(), issue.getOutcomeId())
                    .map(this::toOutcomeContext)
                    .orElse(null);
        } catch (Exception e) {
            LOG.debug("Unable to resolve outcome context for issue {}: {}",
                    issue.getId(), e.getMessage());
            return null;
        }
    }

    private List<IssueEmbeddingTemplate.ReconciliationHistoryEntry> buildReconciliationHistory(
            List<WeeklyAssignmentEntity> assignments
    ) {
        return assignments.stream()
                .sorted(Comparator.comparing(WeeklyAssignmentEntity::getCreatedAt).reversed())
                .map(this::toReconciliationHistoryEntry)
                .filter(Objects::nonNull)
                .toList();
    }

    private IssueEmbeddingTemplate.ReconciliationHistoryEntry toReconciliationHistoryEntry(
            WeeklyAssignmentEntity assignment
    ) {
        WeeklyAssignmentActualEntity actual = assignmentActualRepository
                .findByAssignmentId(assignment.getId())
                .orElse(null);
        if (actual == null) {
            return null;
        }
        String weekLabel = assignment.getCreatedAt()
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .toString();
        return new IssueEmbeddingTemplate.ReconciliationHistoryEntry(
                weekLabel,
                actual.getCompletionStatus().name(),
                actual.getHoursSpent(),
                actual.getActualResult()
        );
    }

    private boolean hasSnapshotContext(WeeklyAssignmentEntity assignment) {
        return assignment.getSnapshotRallyCryName() != null
                || assignment.getSnapshotObjectiveName() != null
                || assignment.getSnapshotOutcomeName() != null;
    }

    private IssueEmbeddingTemplate.OutcomeContext toOutcomeContext(RcdoOutcomeDetail detail) {
        return new IssueEmbeddingTemplate.OutcomeContext(
                detail.rallyCryName(),
                detail.objectiveName(),
                detail.outcomeName()
        );
    }

    private static Map<String, Object> buildMetadata(
            IssueEntity issue,
            String teamName,
            IssueEmbeddingTemplate.OutcomeContext outcomeContext
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("issueKey", issue.getIssueKey());
        metadata.put("teamId", issue.getTeamId().toString());
        metadata.put("orgId", issue.getOrgId().toString());
        metadata.put("status", issue.getStatus().name());
        if (teamName != null && !teamName.isBlank()) {
            metadata.put("teamName", teamName);
        }
        if (issue.getOutcomeId() != null) {
            metadata.put("outcomeId", issue.getOutcomeId().toString());
        }
        if (outcomeContext != null && outcomeContext.outcomeName() != null
                && !outcomeContext.outcomeName().isBlank()) {
            metadata.put("outcomeName", outcomeContext.outcomeName());
        }
        if (issue.getEffortType() != null) {
            metadata.put("effortType", issue.getEffortType().name());
        }
        if (issue.getAssigneeUserId() != null) {
            metadata.put("assigneeUserId", issue.getAssigneeUserId().toString());
        }
        return metadata;
    }
}
