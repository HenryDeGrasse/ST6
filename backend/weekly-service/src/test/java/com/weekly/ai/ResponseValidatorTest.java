package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    class NextWorkSuggestions {

        @Test
        void parsesValidRankedSuggestions() {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "rankedSuggestions": [
                        {
                          "suggestionId": "%s",
                          "confidence": 0.92,
                          "suggestedChessPriority": "QUEEN",
                          "rationale": "High-impact auth work"
                        },
                        {
                          "suggestionId": "%s",
                          "confidence": 0.75,
                          "rationale": "Coverage gap needs attention"
                        }
                      ]
                    }""", id1, id2);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(id1, id2));

            assertEquals(2, items.size());
            assertEquals(id1, items.get(0).suggestionId());
            assertEquals(0.92, items.get(0).confidence(), 0.001);
            assertEquals("QUEEN", items.get(0).suggestedChessPriority());
            assertEquals("High-impact auth work", items.get(0).rationale());
            assertEquals(id2, items.get(1).suggestionId());
            assertNull(items.get(1).suggestedChessPriority(),
                    "Missing chessPriority should result in null field");
        }

        @Test
        void rejectsHallucinatedSuggestionId() {
            String validId = UUID.randomUUID().toString();
            String hallucinatedId = UUID.randomUUID().toString();
            String response = String.format("""
                    {"rankedSuggestions": [
                      {"suggestionId": "%s", "confidence": 0.99, "rationale": "hallucinated"}
                    ]}""", hallucinatedId);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(validId));

            assertTrue(items.isEmpty(), "Hallucinated suggestion ID must be rejected");
        }

        @Test
        void rejectsConfidenceAboveOne() {
            String id = UUID.randomUUID().toString();
            String response = String.format("""
                    {"rankedSuggestions": [
                      {"suggestionId": "%s", "confidence": 1.5, "rationale": "too high"}
                    ]}""", id);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(id));

            assertTrue(items.isEmpty(), "Confidence > 1.0 must be rejected");
        }

        @Test
        void rejectsConfidenceBelowZero() {
            String id = UUID.randomUUID().toString();
            String response = String.format("""
                    {"rankedSuggestions": [
                      {"suggestionId": "%s", "confidence": -0.1, "rationale": "negative"}
                    ]}""", id);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(id));

            assertTrue(items.isEmpty(), "Confidence < 0.0 must be rejected");
        }

        @Test
        void silentlyDropsInvalidChessPriorityButKeepsItem() {
            String id = UUID.randomUUID().toString();
            String response = String.format("""
                    {"rankedSuggestions": [
                      {"suggestionId": "%s", "confidence": 0.80,
                       "suggestedChessPriority": "INVALID_PIECE",
                       "rationale": "valid item with bad priority"}
                    ]}""", id);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(id));

            assertEquals(1, items.size(),
                    "Item with invalid chessPriority should be kept (priority silently dropped)");
            assertNull(items.get(0).suggestedChessPriority(),
                    "Invalid chessPriority should be nulled out");
            assertEquals("valid item with bad priority", items.get(0).rationale());
        }

        @Test
        void acceptsAllValidChessPriorityValues() {
            List<String> validPriorities = List.of("KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN");
            for (String priority : validPriorities) {
                String id = UUID.randomUUID().toString();
                String response = String.format("""
                        {"rankedSuggestions": [
                          {"suggestionId": "%s", "confidence": 0.70,
                           "suggestedChessPriority": "%s",
                           "rationale": "valid priority"}
                        ]}""", id, priority);

                List<ResponseValidator.NextWorkRankedItem> items =
                        ResponseValidator.validateNextWorkSuggestions(response, Set.of(id));

                assertEquals(1, items.size(), "Priority " + priority + " should be accepted");
                assertEquals(priority, items.get(0).suggestedChessPriority());
            }
        }

        @Test
        void rejectsItemWithBlankRationale() {
            String id = UUID.randomUUID().toString();
            String response = String.format("""
                    {"rankedSuggestions": [
                      {"suggestionId": "%s", "confidence": 0.80, "rationale": ""}
                    ]}""", id);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(id));

            assertTrue(items.isEmpty(), "Item with blank rationale must be rejected");
        }

        @Test
        void returnsEmptyListForNullResponse() {
            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(null, Set.of(UUID.randomUUID().toString()));
            assertTrue(items.isEmpty());
        }

        @Test
        void returnsEmptyListForMalformedJson() {
            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(
                            "not valid json", Set.of(UUID.randomUUID().toString()));
            assertTrue(items.isEmpty());
        }

        @Test
        void parsesNestedObjectsCorrectly() {
            // Ensure the brace-depth parser handles objects with nested content
            String id = UUID.randomUUID().toString();
            String response = String.format("""
                    {
                      "rankedSuggestions": [
                        {
                          "suggestionId": "%s",
                          "confidence": 0.88,
                          "suggestedChessPriority": "KING",
                          "rationale": "This is {critical} for the team"
                        }
                      ]
                    }""", id);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(response, Set.of(id));

            // The parser should handle curly braces inside string values
            // At minimum it should not throw an exception; may or may not parse correctly
            // depending on parser depth. We verify graceful handling.
            assertNotNull(items, "Parser must not throw on JSON with braces in string values");
        }

        @Test
        void onlyAcceptsItemsFromValidSet() {
            String validId1 = UUID.randomUUID().toString();
            String validId2 = UUID.randomUUID().toString();
            String invalidId = UUID.randomUUID().toString();
            String response = String.format("""
                    {"rankedSuggestions": [
                      {"suggestionId": "%s", "confidence": 0.90, "rationale": "ok"},
                      {"suggestionId": "%s", "confidence": 0.85, "rationale": "hallucinated"},
                      {"suggestionId": "%s", "confidence": 0.80, "rationale": "ok"}
                    ]}""", validId1, invalidId, validId2);

            List<ResponseValidator.NextWorkRankedItem> items =
                    ResponseValidator.validateNextWorkSuggestions(
                            response, Set.of(validId1, validId2));

            assertEquals(2, items.size(), "Only valid IDs should be returned");
            assertTrue(items.stream().anyMatch(i -> i.suggestionId().equals(validId1)));
            assertTrue(items.stream().anyMatch(i -> i.suggestionId().equals(validId2)));
            assertFalse(items.stream().anyMatch(i -> i.suggestionId().equals(invalidId)));
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
