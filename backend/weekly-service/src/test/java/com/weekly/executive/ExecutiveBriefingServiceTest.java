package com.weekly.executive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.LlmClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutiveBriefingServiceTest {

    private LlmClient llmClient;
    private ExecutiveBriefingService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        service = new ExecutiveBriefingService(llmClient, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void returnsParsedBriefingWhenLlmSucceeds() {
        when(llmClient.complete(any(), eq(ExecutiveBriefingService.responseSchema()))).thenReturn("""
                {
                  "headline": "Forecast confidence is mixed across the portfolio.",
                  "insights": [
                    {
                      "title": "Capacity remains mostly strategic",
                      "detail": "Strategic utilization is above 70% this week.",
                      "severity": "POSITIVE"
                    },
                    {
                      "title": "One bucket trails",
                      "detail": "Team-2 trails the rest of the org on confidence.",
                      "severity": "WARNING"
                    }
                  ]
                }
                """);

        ExecutiveBriefingService.ExecutiveBriefingResult result = service.createBriefing(
                UUID.randomUUID(),
                dashboard());

        assertEquals("ok", result.status());
        assertEquals(2, result.insights().size());
        assertEquals("POSITIVE", result.insights().getFirst().severity());
    }

    @Test
    void returnsUnavailableWhenLlmUnavailable() {
        when(llmClient.complete(any(), eq(ExecutiveBriefingService.responseSchema())))
                .thenThrow(new LlmClient.LlmUnavailableException("timeout"));

        ExecutiveBriefingService.ExecutiveBriefingResult result = service.createBriefing(
                UUID.randomUUID(),
                dashboard());

        assertEquals("unavailable", result.status());
        assertTrue(result.insights().isEmpty());
    }

    @Test
    void returnsUnavailableWhenLlmReturnsTooFewInsights() {
        when(llmClient.complete(any(), eq(ExecutiveBriefingService.responseSchema()))).thenReturn("""
                {
                  "headline": "Forecast confidence is mixed across the portfolio.",
                  "insights": [
                    {
                      "title": "Capacity remains mostly strategic",
                      "detail": "Strategic utilization is above 70% this week.",
                      "severity": "POSITIVE"
                    }
                  ]
                }
                """);

        ExecutiveBriefingService.ExecutiveBriefingResult result = service.createBriefing(
                UUID.randomUUID(),
                dashboard());

        assertEquals("unavailable", result.status());
        assertTrue(result.insights().isEmpty());
    }

    private ExecutiveDashboardService.ExecutiveDashboardResult dashboard() {
        return new ExecutiveDashboardService.ExecutiveDashboardResult(
                LocalDate.of(2026, 3, 23),
                new ExecutiveDashboardService.ExecutiveSummary(
                        2,
                        1,
                        1,
                        0,
                        0,
                        new BigDecimal("0.7000"),
                        new BigDecimal("75.0"),
                        new BigDecimal("55.0"),
                        new BigDecimal("20.0"),
                        new BigDecimal("73.33"),
                        new BigDecimal("26.67"),
                        new BigDecimal("100.00")),
                List.of(new ExecutiveDashboardService.RallyCryHealthRollup(
                        UUID.randomUUID().toString(),
                        "Customer Growth",
                        2,
                        1,
                        1,
                        0,
                        0,
                        new BigDecimal("0.7000"),
                        new BigDecimal("22.0"))),
                List.of(new ExecutiveDashboardService.TeamBucketComparison(
                        "team-1",
                        3,
                        new BigDecimal("100.00"),
                        new BigDecimal("50.0"),
                        new BigDecimal("36.0"),
                        new BigDecimal("14.0"),
                        new BigDecimal("72.00"),
                        new BigDecimal("0.7800"))),
                true);
    }
}
