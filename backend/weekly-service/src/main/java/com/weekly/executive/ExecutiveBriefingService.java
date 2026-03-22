package com.weekly.executive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.LlmClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds an executive briefing grounded in aggregate strategic-health metrics.
 */
@Service
public class ExecutiveBriefingService {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutiveBriefingService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ExecutiveBriefingService(
            LlmClient llmClient,
            ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public ExecutiveBriefingResult createBriefing(UUID orgId, ExecutiveDashboardService.ExecutiveDashboardResult dashboard) {
        try {
            List<LlmClient.Message> messages = buildMessages(dashboard);
            String rawResponse = llmClient.complete(messages, responseSchema());
            return parse(rawResponse);
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for executive-briefing: {}", e.getMessage());
            return ExecutiveBriefingResult.unavailable();
        } catch (Exception e) {
            LOG.error("Unexpected error in executive-briefing for org {}", orgId, e);
            return ExecutiveBriefingResult.unavailable();
        }
    }

    List<LlmClient.Message> buildMessages(ExecutiveDashboardService.ExecutiveDashboardResult dashboard) {
        List<LlmClient.Message> messages = new ArrayList<>();
        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are an executive strategy copilot. Summarize only the aggregate metrics provided.
                Produce one headline and 2 to 4 briefing items.

                Rules:
                1. Use only the supplied metrics. Do not invent organisations, teams, people, or causes.
                2. Emphasise forecast health, strategic-vs-non-strategic capacity allocation, and team-level outliers.
                3. Keep each detail sentence concrete and metric-grounded.
                4. Severity must be INFO, WARNING, or POSITIVE.
                5. Respond ONLY with valid JSON matching the required schema.
                """));
        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, buildDashboardContext(dashboard)));
        return messages;
    }

    private String buildDashboardContext(ExecutiveDashboardService.ExecutiveDashboardResult dashboard) {
        StringBuilder context = new StringBuilder();
        ExecutiveDashboardService.ExecutiveSummary summary = dashboard.summary();
        context.append("Executive strategic health dashboard for week ")
                .append(dashboard.weekStart())
                .append(":\n");
        context.append(String.format(Locale.ROOT,
                "Summary: totalForecasts=%d | onTrack=%d | needsAttention=%d | offTrack=%d | noData=%d | avgForecastConfidence=%s | totalCapacityHours=%s | strategicHours=%s | nonStrategicHours=%s | strategicUtilizationPct=%s | nonStrategicUtilizationPct=%s | planningCoveragePct=%s%n",
                summary.totalForecasts(),
                summary.onTrackForecasts(),
                summary.needsAttentionForecasts(),
                summary.offTrackForecasts(),
                summary.noDataForecasts(),
                summary.averageForecastConfidence(),
                summary.totalCapacityHours(),
                summary.strategicHours(),
                summary.nonStrategicHours(),
                summary.strategicCapacityUtilizationPct(),
                summary.nonStrategicCapacityUtilizationPct(),
                summary.planningCoveragePct()));

        context.append("Rally cry rollups:\n");
        for (ExecutiveDashboardService.RallyCryHealthRollup rollup : dashboard.rallyCryRollups().stream().limit(6).toList()) {
            context.append(String.format(Locale.ROOT,
                    "- rallyCryId: %s | rallyCryName: %s | forecastedOutcomeCount: %d | onTrack: %d | needsAttention: %d | offTrack: %d | noData: %d | avgForecastConfidence: %s | strategicHours: %s%n",
                    rollup.rallyCryId(),
                    rollup.rallyCryName(),
                    rollup.forecastedOutcomeCount(),
                    rollup.onTrackCount(),
                    rollup.needsAttentionCount(),
                    rollup.offTrackCount(),
                    rollup.noDataCount(),
                    rollup.averageForecastConfidence(),
                    rollup.strategicHours()));
        }

        context.append("Aggregate team buckets:\n");
        for (ExecutiveDashboardService.TeamBucketComparison bucket : dashboard.teamBuckets()) {
            context.append(String.format(Locale.ROOT,
                    "- bucketId: %s | memberCount: %d | planCoveragePct: %s | totalCapacityHours: %s | strategicHours: %s | nonStrategicHours: %s | strategicUtilizationPct: %s | avgForecastConfidence: %s%n",
                    bucket.bucketId(),
                    bucket.memberCount(),
                    bucket.planCoveragePct(),
                    bucket.totalCapacityHours(),
                    bucket.strategicHours(),
                    bucket.nonStrategicHours(),
                    bucket.strategicCapacityUtilizationPct(),
                    bucket.averageForecastConfidence()));
        }
        return context.toString();
    }

    private ExecutiveBriefingResult parse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        String headline = text(root, "headline");
        if (headline == null || headline.isBlank()) {
            return ExecutiveBriefingResult.unavailable();
        }

        List<ExecutiveBriefingItem> items = new ArrayList<>();
        JsonNode insights = root.path("insights");
        if (insights.isArray()) {
            for (JsonNode insight : insights) {
                String title = text(insight, "title");
                String detail = text(insight, "detail");
                String severity = text(insight, "severity");
                if (title == null || title.isBlank() || detail == null || detail.isBlank()) {
                    continue;
                }
                String normalizedSeverity = normalizeSeverity(severity);
                if (normalizedSeverity == null) {
                    continue;
                }
                items.add(new ExecutiveBriefingItem(title, detail, normalizedSeverity));
            }
        }

        if (items.size() < 2 || items.size() > 4) {
            return ExecutiveBriefingResult.unavailable();
        }

        return new ExecutiveBriefingResult("ok", headline, items);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String normalizeSeverity(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INFO", "WARNING", "POSITIVE" -> normalized;
            default -> null;
        };
    }

    static String responseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["headline", "insights"],
                  "additionalProperties": false,
                  "properties": {
                    "headline": {
                      "type": "string"
                    },
                    "insights": {
                      "type": "array",
                      "minItems": 2,
                      "maxItems": 4,
                      "items": {
                        "type": "object",
                        "required": ["title", "detail", "severity"],
                        "additionalProperties": false,
                        "properties": {
                          "title": { "type": "string" },
                          "detail": { "type": "string" },
                          "severity": {
                            "type": "string",
                            "enum": ["INFO", "WARNING", "POSITIVE"]
                          }
                        }
                      }
                    }
                  }
                }
                """;
    }

    public record ExecutiveBriefingResult(String status, String headline, List<ExecutiveBriefingItem> insights) {
        static ExecutiveBriefingResult unavailable() {
            return new ExecutiveBriefingResult("unavailable", null, List.of());
        }
    }

    public record ExecutiveBriefingItem(String title, String detail, String severity) {
    }
}
