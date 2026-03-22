package com.weekly.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.WeeklyServiceApplication;
import com.weekly.auth.InMemoryOrgGraphClient;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for the manager planning-copilot suggestion → apply draft flow.
 */
@SpringBootTest(classes = WeeklyServiceApplication.class, properties = {
        "ai.provider=stub",
        "ai.features.planning-copilot-enabled=true",
        "tenant.rls.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlanningCopilotFlowIntegrationTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000010");
    private static final UUID OUTCOME_ID = UUID.fromString("20000000-0000-0000-0000-000000000010");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);
    private static final String TOKEN = "Bearer dev:" + MANAGER_ID + ":" + ORG_ID + ":MANAGER,IC";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryOrgGraphClient orgGraphClient;

    @Autowired
    private WeeklyPlanRepository weeklyPlanRepository;

    @Autowired
    private WeeklyCommitRepository weeklyCommitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PlanningCopilotService planningCopilotService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM outbox_events");
        jdbcTemplate.execute("DELETE FROM audit_events");
        jdbcTemplate.execute("DELETE FROM weekly_commit_actuals");
        jdbcTemplate.execute("DELETE FROM weekly_commits");
        jdbcTemplate.execute("DELETE FROM weekly_plans");
        orgGraphClient.clear();
        orgGraphClient.registerUser(MANAGER_ID, "Carol Manager", "UTC");
        orgGraphClient.registerUser(REPORT_ID, "Alex Report", "UTC");
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(REPORT_ID));
    }

    @Test
    void suggestionCanBeAppliedIntoManagerSafeDrafts() throws Exception {
        PlanningCopilotService.TeamPlanSuggestionResult suggestion = new PlanningCopilotService.TeamPlanSuggestionResult(
                "ok",
                WEEK_START,
                new PlanningCopilotService.TeamPlanSummary(
                        new BigDecimal("32.0"),
                        new BigDecimal("8.0"),
                        new BigDecimal("24.0"),
                        1,
                        1,
                        new BigDecimal("0.75"),
                        "Focus Alex on the retention gap."),
                List.of(new PlanningCopilotService.MemberPlanSuggestion(
                        REPORT_ID.toString(),
                        "Alex Report",
                        List.of(new PlanningCopilotService.SuggestedCommit(
                                "Reach out to churn-risk customers",
                                OUTCOME_ID.toString(),
                                "QUEEN",
                                new BigDecimal("6.0"),
                                "Protect the at-risk retention outcome.",
                                "DETERMINISTIC")),
                        new BigDecimal("6.0"),
                        new BigDecimal("24.0"),
                        "LOW",
                        "Strong customer follow-through")),
                List.of(),
                false);
        when(planningCopilotService.suggestTeamPlan(eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START))).thenReturn(suggestion);

        mockMvc.perform(post("/api/v1/ai/team-plan-suggestion")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WeekRequest(WEEK_START.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.members[0].userId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.members[0].suggestedCommits[0].title").value("Reach out to churn-risk customers"));

        mockMvc.perform(post("/api/v1/ai/team-plan-suggestion/apply")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApplyRequest(
                                WEEK_START.toString(),
                                List.of(new ApplyMemberRequest(
                                        REPORT_ID.toString(),
                                        List.of(new ApplyCommitRequest(
                                                "Reach out to churn-risk customers",
                                                OUTCOME_ID.toString(),
                                                "Protect the at-risk retention outcome.",
                                                "QUEEN",
                                                6.0))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.members[0].createdPlan").value(true))
                .andExpect(jsonPath("$.members[0].appliedCommits[0].title").value("Reach out to churn-risk customers"))
                .andExpect(jsonPath("$.members[0].appliedCommits[0].description").value("Protect the at-risk retention outcome."));

        WeeklyPlanEntity persistedPlan = weeklyPlanRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, REPORT_ID, WEEK_START)
                .orElseThrow();
        List<WeeklyCommitEntity> persistedCommits = weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, persistedPlan.getId());

        assertThat(persistedCommits).hasSize(1);
        WeeklyCommitEntity commit = persistedCommits.getFirst();
        assertThat(commit.getTitle()).isEqualTo("Reach out to churn-risk customers");
        assertThat(commit.getDescription()).isEqualTo("Protect the at-risk retention outcome.");
        assertThat(commit.getOutcomeId()).isEqualTo(OUTCOME_ID);
        assertThat(commit.getEstimatedHours()).isEqualByComparingTo("6.0");
        assertThat(commit.getTags()).contains(PlanningCopilotDraftApplyService.AI_PLANNED_TAG);
    }

    private record WeekRequest(String weekStart) {
    }

    private record ApplyRequest(String weekStart, List<ApplyMemberRequest> members) {
    }

    private record ApplyMemberRequest(String userId, List<ApplyCommitRequest> suggestedCommits) {
    }

    private record ApplyCommitRequest(
            String title,
            String outcomeId,
            String rationale,
            String chessPriority,
            Double estimatedHours) {
    }
}
