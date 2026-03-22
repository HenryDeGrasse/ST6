package com.weekly.ai.rag;

import com.weekly.ai.LlmClient;
import com.weekly.ai.PromptBuilder;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * HyDE (Hypothetical Document Embeddings) query service (Phase 6, Step 13).
 *
 * <p>Generates a hypothetical issue-like document via the LLM, embeds it, then queries the
 * vector store for similar real issues. All results are org-scoped and may additionally be
 * restricted to a permitted team set supplied by the caller.
 */
@Service
public class HydeQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(HydeQueryService.class);
    private static final int DEFAULT_TOP_K = 10;
    private static final int CANDIDATE_MULTIPLIER = 5;

    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final IssueRepository issueRepository;

    public HydeQueryService(
            LlmClient llmClient,
            EmbeddingClient embeddingClient,
            IssueRepository issueRepository
    ) {
        this.llmClient = llmClient;
        this.embeddingClient = embeddingClient;
        this.issueRepository = issueRepository;
    }

    /**
     * Returns a list of recommended issues for the user's next week using HyDE.
     */
    public List<IssueId> recommendWithHyde(
            UserWorkContext userContext,
            OutcomeRiskContext riskContext,
            int topK
    ) {
        int safeTopK = normalizeTopK(topK);
        try {
            List<LlmClient.Message> messages =
                    PromptBuilder.buildHydeRecommendationPrompt(userContext, riskContext);
            String hypotheticalDoc = llmClient.complete(messages, null);
            LOG.debug("HyDE hypothetical doc for user {}: [{}]", userContext.userId(),
                    hypotheticalDoc.substring(0, Math.min(80, hypotheticalDoc.length())));

            float[] queryVector = embeddingClient.embed(hypotheticalDoc);

            Map<String, Object> filter = new HashMap<>();
            filter.put("orgId", userContext.orgId().toString());
            filter.put("status", IssueStatus.OPEN.name());

            List<ScoredMatch> matches = embeddingClient.query(
                    queryVector,
                    expandedCandidateCount(safeTopK),
                    filter
            );

            return toIssueIds(filterByPermittedTeams(matches, userContext.accessibleTeamIds()), safeTopK);
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for HyDE recommendation (user={}): {}",
                    userContext.userId(), e.getMessage());
            return fallbackByRank(
                    userContext.orgId(),
                    null,
                    userContext.accessibleTeamIds(),
                    IssueStatus.OPEN,
                    true,
                    safeTopK
            );
        } catch (Exception e) {
            LOG.error("HyDE recommendation failed for user {}: {}",
                    userContext.userId(), e.getMessage(), e);
            return fallbackByRank(
                    userContext.orgId(),
                    null,
                    userContext.accessibleTeamIds(),
                    IssueStatus.OPEN,
                    true,
                    safeTopK
            );
        }
    }

    /**
     * Semantic search over the org's issue backlog using HyDE.
     */
    public List<IssueId> searchWithHyde(
            UUID orgId,
            String query,
            int topK,
            Map<String, Object> filters
    ) {
        return searchWithHyde(orgId, query, topK, filters, List.of());
    }

    /**
     * Org-scoped semantic search with optional permitted-team filtering.
     */
    public List<IssueId> searchWithHyde(
            UUID orgId,
            String query,
            int topK,
            Map<String, Object> filters,
            List<UUID> permittedTeamIds
    ) {
        int safeTopK = normalizeTopK(topK);
        try {
            List<LlmClient.Message> queryMessages = buildSearchHydeMessages(query);
            String hypotheticalDoc = llmClient.complete(queryMessages, null);

            float[] queryVector = embeddingClient.embed(hypotheticalDoc);

            Map<String, Object> filter = new HashMap<>();
            filter.put("orgId", orgId.toString());
            if (filters != null) {
                filter.putAll(filters);
            }

            List<ScoredMatch> matches = embeddingClient.query(
                    queryVector,
                    expandedCandidateCount(safeTopK),
                    filter
            );
            return toIssueIds(filterByPermittedTeams(matches, permittedTeamIds), safeTopK);
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for HyDE search (org={}): {}", orgId, e.getMessage());
            return fallbackByRank(
                    orgId,
                    parseTeamId(filters),
                    permittedTeamIds,
                    parseStatus(filters),
                    parseStatus(filters) == null,
                    safeTopK
            );
        } catch (Exception e) {
            LOG.error("HyDE search failed for org {}: {}", orgId, e.getMessage(), e);
            return fallbackByRank(
                    orgId,
                    parseTeamId(filters),
                    permittedTeamIds,
                    parseStatus(filters),
                    parseStatus(filters) == null,
                    safeTopK
            );
        }
    }

    /**
     * Finds issues similar to a given issue using its current content as the query vector.
     */
    public List<IssueId> findSimilar(UUID orgId, UUID issueId, int topK) {
        int safeTopK = normalizeTopK(topK);
        try {
            IssueEntity source = issueRepository.findByOrgIdAndId(orgId, issueId)
                    .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));

            String sourceText = source.getTitle()
                    + (source.getDescription() != null && !source.getDescription().isBlank()
                    ? "\n" + source.getDescription() : "");
            float[] sourceVector = embeddingClient.embed(sourceText);

            Map<String, Object> filter = new HashMap<>();
            filter.put("orgId", orgId.toString());

            List<ScoredMatch> matches = embeddingClient.query(
                    sourceVector,
                    safeTopK + 1,
                    filter
            );
            return matches.stream()
                    .filter(m -> !m.id().equals(issueId.toString()))
                    .map(m -> new IssueId(UUID.fromString(m.id()), m.score()))
                    .limit(safeTopK)
                    .toList();
        } catch (Exception e) {
            LOG.error("findSimilar failed for issue {}: {}", issueId, e.getMessage(), e);
            return fallbackByRank(orgId, null, List.of(), null, true, safeTopK);
        }
    }

    private static List<LlmClient.Message> buildSearchHydeMessages(String query) {
        return List.of(
                new LlmClient.Message(
                        LlmClient.Role.SYSTEM,
                        """
                        You are a work planning assistant. The user will give you a search query.
                        Rewrite it as a hypothetical issue title + description (2-3 sentences) that
                        would appear in a team's backlog and match the user's intent.
                        Write ONLY the hypothetical issue document — no explanation or metadata.
                        """
                ),
                new LlmClient.Message(
                        LlmClient.Role.USER,
                        "Search query: " + query
                )
        );
    }

    private List<IssueId> fallbackByRank(
            UUID orgId,
            UUID requestedTeamId,
            List<UUID> permittedTeamIds,
            IssueStatus status,
            boolean excludeArchivedWhenStatusMissing,
            int topK
    ) {
        try {
            PageRequest pageable = PageRequest.of(
                    0,
                    topK,
                    Sort.by(Sort.Order.asc("aiRecommendedRank").nullsLast(),
                            Sort.Order.desc("createdAt"))
            );

            Page<IssueEntity> page;
            if (requestedTeamId != null) {
                if (permittedTeamIds != null && !permittedTeamIds.isEmpty()
                        && !permittedTeamIds.contains(requestedTeamId)) {
                    return List.of();
                }
                page = status != null
                        ? issueRepository.findAllByOrgIdAndTeamIdInAndStatus(
                                orgId, List.of(requestedTeamId), status, pageable)
                        : issueRepository.findAllByOrgIdAndTeamIdInAndStatusNot(
                                orgId, List.of(requestedTeamId), IssueStatus.ARCHIVED, pageable);
            } else if (permittedTeamIds != null && !permittedTeamIds.isEmpty()) {
                page = status != null
                        ? issueRepository.findAllByOrgIdAndTeamIdInAndStatus(
                                orgId, permittedTeamIds, status, pageable)
                        : issueRepository.findAllByOrgIdAndTeamIdInAndStatusNot(
                                orgId, permittedTeamIds, IssueStatus.ARCHIVED, pageable);
            } else if (status != null) {
                page = issueRepository.findAllByOrgIdAndStatus(orgId, status, pageable);
            } else if (excludeArchivedWhenStatusMissing) {
                page = issueRepository.findAllByOrgIdAndStatusNot(orgId, IssueStatus.ARCHIVED, pageable);
            } else {
                page = issueRepository.findAllByOrgIdAndStatusNot(orgId, IssueStatus.ARCHIVED, pageable);
            }

            return page.getContent().stream()
                    .map(i -> new IssueId(i.getId(), 0.0f))
                    .toList();
        } catch (Exception e) {
            LOG.error("Fallback DB query failed for org {}: {}", orgId, e.getMessage(), e);
            return List.of();
        }
    }

    private static List<ScoredMatch> filterByPermittedTeams(
            List<ScoredMatch> matches,
            List<UUID> permittedTeamIds
    ) {
        if (permittedTeamIds == null || permittedTeamIds.isEmpty()) {
            return matches;
        }
        return matches.stream()
                .filter(match -> {
                    Object teamIdValue = match.metadata().get("teamId");
                    if (!(teamIdValue instanceof String teamIdString)) {
                        return false;
                    }
                    try {
                        return permittedTeamIds.contains(UUID.fromString(teamIdString));
                    } catch (IllegalArgumentException ignored) {
                        return false;
                    }
                })
                .toList();
    }

    private static List<IssueId> toIssueIds(List<ScoredMatch> matches, int topK) {
        return matches.stream()
                .map(m -> new IssueId(UUID.fromString(m.id()), m.score()))
                .limit(topK)
                .toList();
    }

    private static int normalizeTopK(int topK) {
        return topK > 0 ? topK : DEFAULT_TOP_K;
    }

    private static int expandedCandidateCount(int topK) {
        return Math.max(topK, topK * CANDIDATE_MULTIPLIER);
    }

    private static UUID parseTeamId(Map<String, Object> filters) {
        if (filters == null) {
            return null;
        }
        Object value = filters.get("teamId");
        if (!(value instanceof String teamId) || teamId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(teamId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static IssueStatus parseStatus(Map<String, Object> filters) {
        if (filters == null) {
            return null;
        }
        Object value = filters.get("status");
        if (!(value instanceof String status) || status.isBlank()) {
            return null;
        }
        try {
            return IssueStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * A scored issue result from a vector query or database fallback.
     *
     * @param issueId the issue UUID
     * @param score   cosine similarity score [0, 1]; 0.0 for database-fallback results
     */
    public record IssueId(UUID issueId, float score) {}
}
