package com.weekly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack integration test for the Phase 6 issue + team + assignment lifecycle.
 *
 * <p>Exercises the complete flow against a real Postgres container (with Flyway migrations):
 * <ol>
 *   <li>Create a team.</li>
 *   <li>Create an issue in the team's backlog.</li>
 *   <li>Add a comment and log time (activity log verification).</li>
 *   <li>Create a weekly plan and assign the issue to it via the dual-write endpoint
 *       ({@code POST /weeks/{weekStart}/plan/assignments}).</li>
 *   <li>Lock the plan.</li>
 *   <li>Start reconciliation, submit actuals, submit reconciliation.</li>
 *   <li>Carry the reconciled work forward into next week's draft plan using the legacy
 *       carry-forward endpoint (verifying issue reuse through the dual-write bridge).</li>
 *   <li>Release the carried-forward assignment from the next week's draft plan.</li>
 *   <li>Verify the issue activity log captures the full narrative.</li>
 * </ol>
 *
 * <p>The test uses {@link com.weekly.issues.service.AssignmentService#addToWeekPlan}, which
 * dual-writes both a {@code WeeklyAssignment} and a legacy {@code WeeklyCommit} so the plan
 * lifecycle (lock → reconcile) still passes chess-priority validation.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = WeeklyServiceApplication.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.enabled=true",
        "ai.provider=stub",
        "notification.materializer.enabled=false",
        "tenant.rls.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IssueLifecycleIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("weekly")
                    .withUsername("weekly")
                    .withPassword("weekly")
                    .withInitScript("init-rls-user.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000099");
    private static final UUID USER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000099");
    private static final String TOKEN =
            "Bearer dev:" + USER_ID + ":" + ORG_ID + ":ADMIN,MANAGER,IC";

    // ── Full lifecycle test ──────────────────────────────────────────────────

    /**
     * Exercises the complete flow:
     * create team → create issue → add activity →
     * assign to week (dual-write) → lock → reconcile → submit → carry-forward to next week
     * → release the carried-forward assignment.
     * Verifies the issue activity log captures the full narrative.
     */
    @Test
    void fullIssueLifecycle() throws Exception {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate nextWeekStart = weekStart.plusWeeks(1);

        // ── 1. Create a team ─────────────────────────────────
        String createTeamBody = objectMapper.writeValueAsString(new CreateTeamRequest(
                "Integration Platform Team",
                "IPT",
                "End-to-end integration test team"
        ));

        MvcResult createTeamResult = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTeamBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Platform Team"))
                .andExpect(jsonPath("$.keyPrefix").value("IPT"))
                .andReturn();

        JsonNode teamJson = objectMapper.readTree(createTeamResult.getResponse().getContentAsString());
        String teamId = teamJson.get("id").asText();
        assertThat(teamId).isNotEmpty();

        // ── 2. Create an issue in the team backlog ────────────
        // Issue has chess priority KING and a nonStrategicReason so the dual-write
        // commit passes CommitValidator validation at lock time.
        String createIssueBody = objectMapper.writeValueAsString(new CreateIssueRequest(
                "Implement OAuth 2.0 PKCE flow",
                "Add PKCE support to the authorization flow for public clients.",
                "BUILD",
                12.0,
                "KING",
                null,
                "Phase 6 integration test — PKCE security hardening initiative",
                null,
                null
        ));

        MvcResult createIssueResult = mockMvc.perform(
                        post("/api/v1/teams/{teamId}/issues", teamId)
                                .header("Authorization", TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createIssueBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Implement OAuth 2.0 PKCE flow"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.effortType").value("BUILD"))
                .andReturn();

        JsonNode issueJson = objectMapper.readTree(createIssueResult.getResponse().getContentAsString());
        String issueId = issueJson.get("id").asText();
        String issueKey = issueJson.get("issueKey").asText();
        assertThat(issueKey).startsWith("IPT-");

        // ── 3. Add a comment ──────────────────────────────────
        String commentBody = objectMapper.writeValueAsString(
                new AddCommentRequest("Starting research on PKCE spec — RFC 7636.")
        );
        mockMvc.perform(post("/api/v1/issues/{issueId}/comment", issueId)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityType").value("COMMENT"));

        // ── 4. Log time ───────────────────────────────────────
        String timeBody = objectMapper.writeValueAsString(
                new LogTimeEntryRequest(2.5, "Research phase: read RFC 7636.")
        );
        mockMvc.perform(post("/api/v1/issues/{issueId}/time-entry", issueId)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityType").value("TIME_ENTRY"));

        // ── 5. Create plan and assign issue via dual-write ────
        MvcResult planResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plans", weekStart)
                                .header("Authorization", TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn();

        JsonNode planJson = objectMapper.readTree(planResult.getResponse().getContentAsString());
        String planId = planJson.get("id").asText();

        // Use the assignment-based endpoint (POST /weeks/{weekStart}/plan/assignments)
        // which dual-writes a WeeklyAssignment AND a legacy WeeklyCommit (for plan validation)
        String assignBody = objectMapper.writeValueAsString(new CreateWeeklyAssignmentRequest(
                UUID.fromString(issueId),
                "KING",
                "PKCE flow implemented and tested in staging",
                0.8
        ));

        MvcResult assignResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plan/assignments", weekStart)
                                .header("Authorization", TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assignBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueId").value(issueId))
                .andReturn();

        JsonNode assignmentJson = objectMapper.readTree(assignResult.getResponse().getContentAsString());
        String assignmentId = assignmentJson.get("id").asText();
        // legacyCommitId is the cross-walk to the dual-written WeeklyCommit
        String legacyCommitId = assignmentJson.get("legacyCommitId").asText();
        assertThat(legacyCommitId).isNotEmpty();

        // Verify the issue status transitioned to IN_PROGRESS
        // (GET /issues/{id} returns IssueDetailResponse: {issue: {...}, activities: [...]})
        mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issue.status").value("IN_PROGRESS"));

        // ── 6. Verify plan assignments list ───────────────────
        mockMvc.perform(get("/api/v1/plans/{planId}/assignments", planId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments").isArray())
                .andExpect(jsonPath("$.assignments[0].issueId").value(issueId));

        // ── 7. Lock the plan ───────────────────────────────────
        int planVersion = fetchPlanVersion(planId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("Authorization", TOKEN)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LOCKED"));

        // ── 8. Start reconciliation ───────────────────────────
        planVersion = fetchPlanVersion(planId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("Authorization", TOKEN)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECONCILING"));

        // ── 9. Submit actual for the dual-written legacy commit ───────────
        // Fetch the commit version
        MvcResult commitsResult = mockMvc.perform(
                        get("/api/v1/plans/{planId}/commits", planId)
                                .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode commitsJson = objectMapper.readTree(commitsResult.getResponse().getContentAsString());
        JsonNode commitNode = findNodeById(commitsJson, legacyCommitId);
        assertThat(commitNode).isNotNull();
        int commitVersion = commitNode.get("version").asInt();

        String actualBody = objectMapper.writeValueAsString(new ActualRequest(
                "PKCE flow implemented but needs additional testing",
                "PARTIALLY",
                "Browser compatibility issue discovered — needs a follow-up fix",
                8
        ));
        mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", legacyCommitId)
                        .header("Authorization", TOKEN)
                        .header("If-Match", commitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionStatus").value("PARTIALLY"));

        // ── 10. Submit reconciliation ─────────────────────────
        planVersion = fetchPlanVersion(planId);
        mockMvc.perform(post("/api/v1/plans/{planId}/submit-reconciliation", planId)
                        .header("Authorization", TOKEN)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RECONCILED"));

        // ── 11. Carry forward to next week via the plan lifecycle ─────────
        // This exercises the legacy carry-forward endpoint, which the dual-write
        // layer maps back onto the same persistent issue and mirrored assignment.
        planVersion = fetchPlanVersion(planId);
        String carryForwardBody = objectMapper.writeValueAsString(
                new CarryForwardRequest(java.util.List.of(legacyCommitId))
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/carry-forward", planId)
                        .header("Authorization", TOKEN)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carryForwardBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CARRY_FORWARD"));

        // Retrieve the next week's plan (carry-forward creates it if missing).
        MvcResult nextPlanResult = mockMvc.perform(
                        post("/api/v1/weeks/{weekStart}/plans", nextWeekStart)
                                .header("Authorization", TOKEN))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        JsonNode nextPlanJson = objectMapper.readTree(nextPlanResult.getResponse().getContentAsString());
        String nextPlanId = nextPlanJson.get("id").asText();

        MvcResult nextAssignmentsResult = mockMvc.perform(get("/api/v1/plans/{planId}/assignments", nextPlanId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments").isArray())
                .andExpect(jsonPath("$.assignments[0].issueId").value(issueId))
                .andReturn();

        JsonNode nextAssignmentsJson = objectMapper.readTree(nextAssignmentsResult.getResponse().getContentAsString());
        String nextAssignmentId = nextAssignmentsJson.get("assignments").get(0).get("id").asText();
        assertThat(nextAssignmentId).isNotEmpty();

        // Verify the same issue is still IN_PROGRESS after carry-forward.
        mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issue.status").value("IN_PROGRESS"));

        // ── 12. Release the carried-forward issue back to the backlog ──────
        String releaseBody = objectMapper.writeValueAsString(
                new ReleaseIssueRequest(UUID.fromString(nextPlanId))
        );
        mockMvc.perform(post("/api/v1/issues/{issueId}/release", issueId)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(releaseBody))
                .andExpect(status().isOk())
                // The historical source-week assignment still exists after carry-forward,
                // so the issue remains IN_PROGRESS even though the newly carried assignment
                // was released from the draft plan.
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/plans/{planId}/assignments", nextPlanId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments").isEmpty());

        // ── 13. Verify the activity log captures the full narrative ────────
        MvcResult detailResult = mockMvc.perform(
                        get("/api/v1/issues/{issueId}", issueId)
                                .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        JsonNode activities = detail.get("activities");
        assertThat(activities).isNotNull();
        assertThat(activities.isArray()).isTrue();

        assertThat(activities.size()).isGreaterThanOrEqualTo(6);

        java.util.Set<String> activityTypes = new java.util.HashSet<>();
        for (JsonNode activity : activities) {
            activityTypes.add(activity.get("activityType").asText());
        }
        assertThat(activityTypes)
                .as("Issue activity log should capture the full lifecycle narrative")
                .contains(
                        "COMMENT",
                        "TIME_ENTRY",
                        "COMMITTED_TO_WEEK",
                        "CARRIED_FORWARD",
                        "RELEASED_TO_BACKLOG"
                );

        // ── 14. Verify team backlog shows the issue ────────────
        // IssueListResponse uses "content" (not "issues") for the item list
        mockMvc.perform(get("/api/v1/teams/{teamId}/issues", teamId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int fetchPlanVersion(String planId) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/v1/plans/{planId}", planId)
                                .header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("version").asInt();
    }

    /** Finds a JSON array node by the "id" field. Returns null if not found. */
    private JsonNode findNodeById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.get("id").asText())) {
                return node;
            }
        }
        return null;
    }

    // ── Request record helpers ────────────────────────────────────────────────

    private record CreateTeamRequest(String name, String keyPrefix, String description) {}

    private record CreateIssueRequest(
            String title,
            String description,
            String effortType,
            Double estimatedHours,
            String chessPriority,
            String outcomeId,
            String nonStrategicReason,
            String assigneeUserId,
            String blockedByIssueId
    ) {}

    private record CreateWeeklyAssignmentRequest(
            UUID issueId,
            String chessPriorityOverride,
            String expectedResult,
            Double confidence
    ) {}

    private record CarryForwardRequest(java.util.List<String> commitIds) {}

    private record ReleaseIssueRequest(UUID weeklyPlanId) {}

    private record AddCommentRequest(String commentText) {}

    private record LogTimeEntryRequest(Double hoursLogged, String note) {}

    private record ActualRequest(
            String actualResult,
            String completionStatus,
            String deltaReason,
            Integer timeSpent
    ) {}
}
