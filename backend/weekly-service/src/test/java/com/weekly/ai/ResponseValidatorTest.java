package com.weekly.ai;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ResponseValidator}.
 */
class ResponseValidatorTest {

    @Nested
    class RcdoSuggestions {

        @Test
        void parsesValidResponse() {
            String outcomeId = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "suggestions": [
                        {
                          "outcomeId": "%s",
                          "rallyCryName": "Revenue Growth",
                          "objectiveName": "Enterprise Sales",
                          "outcomeName": "Close Q1 deals",
                          "confidence": 0.87,
                          "rationale": "Keywords match"
                        }
                      ]
                    }""", outcomeId);

            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions(response, Set.of(outcomeId));

            assertEquals(1, suggestions.size());
            assertEquals(outcomeId, suggestions.get(0).outcomeId());
            assertEquals(0.87, suggestions.get(0).confidence(), 0.01);
            assertEquals("Revenue Growth", suggestions.get(0).rallyCryName());
        }

        @Test
        void rejectsHallucinatedOutcomeId() {
            String validId = UUID.randomUUID().toString();
            String fakeId = "hallucinated-id";
            String response = String.format("""
                    {
                      "suggestions": [
                        {
                          "outcomeId": "%s",
                          "rallyCryName": "Fake",
                          "objectiveName": "Fake",
                          "outcomeName": "Fake",
                          "confidence": 0.99,
                          "rationale": "Hallucinated"
                        }
                      ]
                    }""", fakeId);

            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions(response, Set.of(validId));

            assertTrue(suggestions.isEmpty(), "Hallucinated IDs must be rejected");
        }

        @Test
        void rejectsInvalidConfidence() {
            String outcomeId = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "suggestions": [
                        {
                          "outcomeId": "%s",
                          "rallyCryName": "RC",
                          "objectiveName": "OBJ",
                          "outcomeName": "OUT",
                          "confidence": 1.5,
                          "rationale": "Too confident"
                        }
                      ]
                    }""", outcomeId);

            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions(response, Set.of(outcomeId));

            assertTrue(suggestions.isEmpty(), "Confidence > 1 should be rejected");
        }

        @Test
        void returnsEmptyForNullResponse() {
            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions(null, Set.of("id"));
            assertTrue(suggestions.isEmpty());
        }

        @Test
        void returnsEmptyForMalformedJson() {
            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions("not json at all", Set.of("id"));
            assertTrue(suggestions.isEmpty());
        }

        @Test
        void limitsToFiveSuggestions() {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();
            String id3 = UUID.randomUUID().toString();
            String id4 = UUID.randomUUID().toString();
            String id5 = UUID.randomUUID().toString();
            String id6 = UUID.randomUUID().toString();

            StringBuilder sb = new StringBuilder("{\"suggestions\": [");
            for (int i = 0; i < 6; i++) {
                String id = List.of(id1, id2, id3, id4, id5, id6).get(i);
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(String.format("""
                        {"outcomeId": "%s", "rallyCryName": "RC", "objectiveName": "OBJ", "outcomeName": "OUT%d", "confidence": 0.%d, "rationale": "r"}
                        """, id, i, 9 - i));
            }
            sb.append("]}");

            Set<String> validIds = Set.of(id1, id2, id3, id4, id5, id6);
            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions(sb.toString(), validIds);

            assertEquals(5, suggestions.size(), "Should be limited to 5 suggestions");
        }

        @Test
        void sortsByConfidenceDescending() {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "suggestions": [
                        {"outcomeId": "%s", "rallyCryName": "RC1", "objectiveName": "OBJ1", "outcomeName": "OUT1", "confidence": 0.5, "rationale": "Low"},
                        {"outcomeId": "%s", "rallyCryName": "RC2", "objectiveName": "OBJ2", "outcomeName": "OUT2", "confidence": 0.9, "rationale": "High"}
                      ]
                    }""", id1, id2);

            List<AiSuggestionService.RcdoSuggestion> suggestions =
                    ResponseValidator.validateRcdoSuggestions(response, Set.of(id1, id2));

            assertEquals(2, suggestions.size());
            assertEquals(id2, suggestions.get(0).outcomeId(), "Higher confidence should be first");
        }
    }

    @Nested
    class ReconciliationDrafts {

        @Test
        void parsesValidDraftResponse() {
            String commitId = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "drafts": [
                        {
                          "commitId": "%s",
                          "suggestedStatus": "DONE",
                          "suggestedDeltaReason": null,
                          "suggestedActualResult": "Completed successfully"
                        }
                      ]
                    }""", commitId);

            List<AiSuggestionService.ReconciliationDraftItem> drafts =
                    ResponseValidator.validateReconciliationDraft(response, Set.of(commitId));

            assertEquals(1, drafts.size());
            assertEquals(commitId, drafts.get(0).commitId());
            assertEquals("DONE", drafts.get(0).suggestedStatus());
        }

        @Test
        void rejectsInvalidCommitId() {
            String validId = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "drafts": [
                        {
                          "commitId": "fake-id",
                          "suggestedStatus": "DONE",
                          "suggestedActualResult": "Completed"
                        }
                      ]
                    }""");

            List<AiSuggestionService.ReconciliationDraftItem> drafts =
                    ResponseValidator.validateReconciliationDraft(response, Set.of(validId));

            assertTrue(drafts.isEmpty());
        }

        @Test
        void rejectsInvalidStatus() {
            String commitId = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "drafts": [
                        {
                          "commitId": "%s",
                          "suggestedStatus": "INVALID_STATUS",
                          "suggestedActualResult": "Something"
                        }
                      ]
                    }""", commitId);

            List<AiSuggestionService.ReconciliationDraftItem> drafts =
                    ResponseValidator.validateReconciliationDraft(response, Set.of(commitId));

            assertTrue(drafts.isEmpty());
        }
    }

    @Nested
    class ManagerInsights {

        @Test
        void parsesValidManagerInsightsResponse() {
            String response = """
                    {
                      "headline": "The team is mostly aligned, with one review hotspot.",
                      "insights": [
                        {
                          "title": "Review hotspot",
                          "detail": "One report still needs review follow-up.",
                          "severity": "WARNING"
                        }
                      ]
                    }
                    """;

            AiSuggestionService.ManagerInsightsResult result =
                    ResponseValidator.validateManagerInsights(response);

            assertEquals("ok", result.status());
            assertEquals("The team is mostly aligned, with one review hotspot.", result.headline());
            assertEquals(1, result.insights().size());
            assertEquals("WARNING", result.insights().get(0).severity());
        }

        @Test
        void returnsUnavailableForMissingHeadline() {
            AiSuggestionService.ManagerInsightsResult result =
                    ResponseValidator.validateManagerInsights("{\"insights\": []}");

            assertEquals("unavailable", result.status());
            assertTrue(result.insights().isEmpty());
        }
    }
}
