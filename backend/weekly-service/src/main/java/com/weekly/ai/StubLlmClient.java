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
