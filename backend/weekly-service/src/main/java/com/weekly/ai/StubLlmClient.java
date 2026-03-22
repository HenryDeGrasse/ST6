package com.weekly.ai;

import java.util.List;

/**
 * Stub LLM client for development and testing.
 *
 * <p>Returns a deterministic response that suggests the first candidate
 * outcome from the prompt context, allowing the full suggestion pipeline
 * to be exercised without an actual LLM.
 */
public class StubLlmClient implements LlmClient {

    private boolean available = true;
    private String overrideResponse;

    @Override
    public String complete(List<Message> messages, String responseSchema) {
        if (!available) {
            throw new LlmUnavailableException("Stub LLM is configured as unavailable");
        }

        if (overrideResponse != null) {
            return overrideResponse;
        }

        // Manager-insight summary request
        for (Message msg : messages) {
            if (msg.role() == Role.ASSISTANT && msg.content().contains("Manager dashboard context")) {
                return buildDefaultManagerInsightsResponse();
            }
        }

        // Executive strategic-health briefing request
        for (Message msg : messages) {
            if (msg.role() == Role.ASSISTANT && msg.content().contains("Executive strategic health dashboard")) {
                return buildDefaultExecutiveBriefingResponse();
            }
        }

        // Next-work suggestion re-ranking request
        for (Message msg : messages) {
            if (msg.role() == Role.ASSISTANT
                    && msg.content().contains("Candidate suggestions to re-rank")) {
                return buildDefaultNextWorkResponse(msg.content());
            }
        }

        // Effort type classification request
        for (Message msg : messages) {
            if (msg.role() == Role.USER && msg.content().contains("BUILD, MAINTAIN, COLLABORATE, LEARN")) {
                return buildDefaultEffortTypeResponse(msg.content());
            }
        }

        // Extract the candidate outcomes from the ASSISTANT context message
        // and return a response suggesting the first one
        for (Message msg : messages) {
            if (msg.role() == Role.ASSISTANT && msg.content().contains("outcomeId:")) {
                return buildDefaultSuggestResponse(msg.content());
            }
        }

        // If no ASSISTANT context, check if this is a reconciliation request
        for (Message msg : messages) {
            if (msg.role() == Role.USER && msg.content().contains("commitId:")) {
                return buildDefaultDraftResponse(msg.content());
            }
        }

        return "{\"suggestions\": []}";
    }

    private String buildDefaultNextWorkResponse(String candidateContext) {
        // Parse all suggestionId values from the context and return them ranked
        java.util.regex.Pattern idPattern =
                java.util.regex.Pattern.compile("suggestionId:\\s*(\\S+)\\s*\\|");
        java.util.regex.Matcher m = idPattern.matcher(candidateContext);
        StringBuilder sb = new StringBuilder("{\"rankedSuggestions\": [");
        boolean first = true;
        double confidence = 0.90;
        while (m.find()) {
            String id = m.group(1);
            if (!first) {
                sb.append(",");
            }
            sb.append(String.format("""
                    {
                      "suggestionId": "%s",
                      "confidence": %.2f,
                      "suggestedChessPriority": "QUEEN",
                      "rationale": "High-impact item identified by strategic analysis"
                    }""", id, confidence));
            first = false;
            confidence = Math.max(0.50, confidence - 0.05);
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildDefaultSuggestResponse(String candidateContext) {
        // Parse the first candidate from the context
        String[] lines = candidateContext.split("\n");
        for (String line : lines) {
            if (line.contains("outcomeId:")) {
                String outcomeId = extractField(line, "outcomeId:");
                String outcomeName = extractField(line, "outcomeName:");
                String objectiveName = extractField(line, "objectiveName:");
                String rallyCryName = extractField(line, "rallyCryName:");
                if (outcomeId != null) {
                    return String.format("""
                            {
                              "suggestions": [
                                {
                                  "outcomeId": "%s",
                                  "rallyCryName": "%s",
                                  "objectiveName": "%s",
                                  "outcomeName": "%s",
                                  "confidence": 0.85,
                                  "rationale": "Strong alignment based on keyword match"
                                }
                              ]
                            }""", outcomeId,
                            rallyCryName != null ? rallyCryName : "Unknown",
                            objectiveName != null ? objectiveName : "Unknown",
                            outcomeName != null ? outcomeName : "Unknown");
                }
            }
        }
        return "{\"suggestions\": []}";
    }

    private String buildDefaultDraftResponse(String commitContext) {
        StringBuilder sb = new StringBuilder("{\"drafts\": [");
        String[] lines = commitContext.split("\n");
        boolean first = true;
        for (String line : lines) {
            if (line.contains("commitId:")) {
                String commitId = extractField(line, "commitId:");
                if (commitId != null) {
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(String.format("""
                            {
                              "commitId": "%s",
                              "suggestedStatus": "DONE",
                              "suggestedDeltaReason": null,
                              "suggestedActualResult": "Completed as planned"
                            }""", commitId));
                    first = false;
                }
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildDefaultEffortTypeResponse(String userContent) {
        // Simple keyword heuristic for the stub — mirrors the service fallback
        String lower = userContent.toLowerCase(java.util.Locale.ROOT);
        String effortType;
        if (lower.contains("fix") || lower.contains("bug") || lower.contains("patch")
                || lower.contains("incident") || lower.contains("maintain")) {
            effortType = "MAINTAIN";
        } else if (lower.contains("review") || lower.contains("meeting") || lower.contains("mentor")
                || lower.contains("collaborate") || lower.contains("customer")) {
            effortType = "COLLABORATE";
        } else if (lower.contains("learn") || lower.contains("spike") || lower.contains("research")
                || lower.contains("training") || lower.contains("explore")) {
            effortType = "LEARN";
        } else {
            effortType = "BUILD";
        }
        return String.format("{\"effortType\": \"%s\", \"confidence\": 0.85}", effortType);
    }

    private String buildDefaultManagerInsightsResponse() {
        return """
                {
                  "headline": "Team delivery is concentrated in a few strategic outcomes, with modest review backlog risk.",
                  "insights": [
                    {
                      "title": "Review queue is manageable",
                      "detail": "Most plans are already approved or in progress, with only a small number still pending review.",
                      "severity": "INFO"
                    },
                    {
                      "title": "Strategic work is concentrated",
                      "detail": "A small set of outcomes appears to carry most of the team's commitments, which may indicate focus but also concentration risk.",
                      "severity": "WARNING"
                    }
                  ]
                }
                """;
    }

    private String buildDefaultExecutiveBriefingResponse() {
        return """
                {
                  "headline": "Strategic capacity is mostly aligned, but a subset of forecasts still needs intervention.",
                  "insights": [
                    {
                      "title": "Strategic work remains the majority",
                      "detail": "Most tracked capacity this week is still allocated to strategic outcomes rather than non-strategic work.",
                      "severity": "POSITIVE"
                    },
                    {
                      "title": "Forecast health is mixed",
                      "detail": "At least one rally-cry rollup still shows outcomes needing attention or off-track forecasts.",
                      "severity": "WARNING"
                    }
                  ]
                }
                """;
    }

    private String extractField(String line, String fieldName) {
        int idx = line.indexOf(fieldName);
        if (idx < 0) {
            return null;
        }
        String after = line.substring(idx + fieldName.length()).trim();
        int pipeIdx = after.indexOf('|');
        if (pipeIdx >= 0) {
            return after.substring(0, pipeIdx).trim();
        }
        return after.trim();
    }

    // ── Test helpers ─────────────────────────────────────────

    /**
     * Sets whether the LLM is available (for testing error paths).
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * Overrides the response returned by complete() (for testing).
     */
    public void setOverrideResponse(String response) {
        this.overrideResponse = response;
    }

    /**
     * Resets to default behavior.
     */
    public void reset() {
        this.available = true;
        this.overrideResponse = null;
    }
}
