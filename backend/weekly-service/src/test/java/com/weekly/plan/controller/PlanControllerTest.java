package com.weekly.plan.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.InMemoryOrgGraphClient;
import com.weekly.notification.NotificationEntity;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.dto.CarryForwardRequest;
import com.weekly.plan.dto.CreateCommitRequest;
import com.weekly.plan.dto.CreateReviewRequest;
import com.weekly.plan.dto.DraftFromHistoryRequest;
import com.weekly.plan.dto.UpdateActualRequest;
import com.weekly.plan.dto.UpdateCommitRequest;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the Plan, Commit, and Review controllers.
 * Uses an H2 in-memory database with the test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryRcdoClient rcdoClient;

    @Autowired
    private InMemoryOrgGraphClient orgGraphClient;

    @Autowired
    private NotificationRepository notificationRepository;

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @AfterEach
    void clearCaches() {
        rcdoClient.clear();
        orgGraphClient.clear();
    }

    private String currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY).toString();
    }

    private UUID seedRcdoOutcome() {
        UUID outcomeId = UUID.randomUUID();
        UUID objectiveId = UUID.randomUUID();
        UUID rallyCryId = UUID.randomUUID();

        RcdoTree.Outcome outcome = new RcdoTree.Outcome(
                outcomeId.toString(), "Revenue Growth", objectiveId.toString()
        );
        RcdoTree.Objective objective = new RcdoTree.Objective(
                objectiveId.toString(), "Increase ARR", rallyCryId.toString(), List.of(outcome)
        );
        RcdoTree.RallyCry rallyCry = new RcdoTree.RallyCry(
                rallyCryId.toString(), "Scale to $500M", List.of(objective)
        );
        rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(rallyCry)));
        return outcomeId;
    }

    /**
     * Helper: creates a plan, adds valid commits, locks, starts reconciliation,
     * fills actuals, and submits reconciliation.
     * Returns a JSON object with planId, commitId, and planVersion.
     */
    private PlanTestContext createReconciledPlan(UUID userId) throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(userId));

        // Create plan
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        var planJson = objectMapper.readTree(planResult.getResponse().getContentAsString());
        String planId = planJson.get("id").asText();

        // Add commits
        CreateCommitRequest king = new CreateCommitRequest(
                "Ship feature", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Feature live", 0.9, null, null
        );
        MvcResult kingResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated())
                .andReturn();
        var kingJson = objectMapper.readTree(kingResult.getResponse().getContentAsString());
        String commitId = kingJson.get("id").asText();

        CreateCommitRequest queen = new CreateCommitRequest(
                "Support work", null, "QUEEN", "OPERATIONS",
                null, "Admin support", null, null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queen)))
                .andExpect(status().isCreated());

        // Lock
        int planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Start reconciliation
        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Fill actuals for all commits
        MvcResult commitsResult = mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andReturn();
        var commits = objectMapper.readTree(commitsResult.getResponse().getContentAsString());
        for (var commit : commits) {
            String cId = commit.get("id").asText();
            int cVersion = commit.get("version").asInt();
            UpdateActualRequest actual = new UpdateActualRequest(
                    "Completed this task", "DONE", null, 120
            , null);
            mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", cId)
                            .header("X-Org-Id", ORG_ID)
                            .header("X-User-Id", userId)
                            .header("If-Match", cVersion)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(actual)))
                    .andExpect(status().isOk());
        }

        // Submit reconciliation
        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/submit-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, userId);
        return new PlanTestContext(planId, commitId, planVersion, userId);
    }

    private int getVersion(String planId, UUID userId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("version").asInt();
    }

    record PlanTestContext(String planId, String commitId, int planVersion, UUID userId) {}

    // ── Plan CRUD ────────────────────────────────────────────

    @Test
    void createPlanReturns201WithDraftState() throws Exception {
        String weekStart = currentMonday();

        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state", is("DRAFT")))
                .andExpect(jsonPath("$.reviewStatus", is("REVIEW_NOT_APPLICABLE")))
                .andExpect(jsonPath("$.weekStartDate", is(weekStart)))
                .andExpect(jsonPath("$.lockType").value(nullValue()))
                .andExpect(jsonPath("$.lockedAt").value(nullValue()));
    }

    @Test
    void createPlanIdempotentlyReturns200() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        // First call: 201
        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated());

        // Second call: 200
        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk());
    }

    @Test
    void createPlanRejectsNonMonday() throws Exception {
        String tuesday = LocalDate.now().with(DayOfWeek.MONDAY).plusDays(1).toString();

        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", tuesday)
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INVALID_WEEK_START")));
    }

    @Test
    void draftFromHistoryCreatesDraftPlanWhenNoHistoryExists() throws Exception {
        UUID userId = UUID.randomUUID();
        DraftFromHistoryRequest request = new DraftFromHistoryRequest(currentMonday());

        mockMvc.perform(post("/api/v1/plans/draft-from-history")
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId", notNullValue()))
                .andExpect(jsonPath("$.suggestedCommits", hasSize(0)));
    }

    @Test
    void getMyPlanReturnsExistingPlan() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/weeks/{weekStart}/plans/me", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));
    }

    @Test
    void getMyPlanReturns404WhenNotFound() throws Exception {
        String weekStart = currentMonday();

        mockMvc.perform(get("/api/v1/weeks/{weekStart}/plans/me", weekStart)
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void getPlanByIdReturnsExistingPlan() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();

        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(planId)));
    }

    @Test
    void getPlanByIdReturns404EnvelopeWhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}", UUID.randomUUID())
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void getPlanByIdAllowsManagerAccess() throws Exception {
        String weekStart = currentMonday();
        UUID owner = UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Register MANAGER_ID as manager of owner
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(owner));

        // Manager can read the plan
        mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", MANAGER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(planId)));
    }

    @Test
    void getPlanByIdForbidsThirdPartyAccess() throws Exception {
        String weekStart = currentMonday();
        UUID owner = UUID.randomUUID();
        UUID thirdParty = UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // thirdParty is neither the owner nor a registered manager of owner
        mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", thirdParty))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    // ── Commit CRUD ──────────────────────────────────────────

    @Test
    void createAndListCommits() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        // Create plan
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();

        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Create commit
        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Build API", "Implement REST endpoints",
                "KING", "DELIVERY",
                null, "Non-strategic",
                "API ready", 0.85, null, new String[]{"api"}
        );

        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Build API")))
                .andExpect(jsonPath("$.chessPriority", is("KING")));

        // List commits
        mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Build API")));
    }

    @Test
    void updateCommitInDraftState() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        // Create plan and commit
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Task", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        var commitJson = objectMapper.readTree(commitResult.getResponse().getContentAsString());
        String commitId = commitJson.get("id").asText();
        int version = commitJson.get("version").asInt();

        // Update commit
        UpdateCommitRequest updateReq = new UpdateCommitRequest(
                "Updated Task", "Now with description",
                "QUEEN", null, null, null, null, null, null, null, null
        );

        mockMvc.perform(patch("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Task")))
                .andExpect(jsonPath("$.description", is("Now with description")))
                .andExpect(jsonPath("$.chessPriority", is("QUEEN")));
    }

    @Test
    void deleteCommitInDraftState() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        // Create plan and commit
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Delete me", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();

        // Delete commit
        mockMvc.perform(delete("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isNoContent());

        // Verify list is now empty
        mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void commitValidationErrorsShownInDraft() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Incomplete commit", null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationErrors", hasSize(2)))
                .andExpect(jsonPath("$.validationErrors[0].code", notNullValue()));
    }

    @Test
    void lockPlanPopulatesSnapshotsAndFreezesPlanningFields() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        var planJson = objectMapper.readTree(planResult.getResponse().getContentAsString());
        String planId = planJson.get("id").asText();

        CreateCommitRequest strategicCommit = new CreateCommitRequest(
                "Ship API", "Implement the planning endpoints",
                "KING", "DELIVERY", outcomeId.toString(), null,
                "API available", 0.9, null, new String[]{"api", "backend"}
        );
        MvcResult strategicCommitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(strategicCommit)))
                .andExpect(status().isCreated())
                .andReturn();
        var strategicCommitJson = objectMapper.readTree(strategicCommitResult.getResponse().getContentAsString());
        String strategicCommitId = strategicCommitJson.get("id").asText();

        CreateCommitRequest supportCommit = new CreateCommitRequest(
                "Support team", null,
                "QUEEN", "OPERATIONS", null, "Operational support",
                null, null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supportCommit)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, userId);

        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("LOCKED")))
                .andExpect(jsonPath("$.lockType", is("ON_TIME")));

        MvcResult commitsAfterLock = mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andReturn();
        var commitsJson = objectMapper.readTree(commitsAfterLock.getResponse().getContentAsString());
        var lockedStrategicCommit = java.util.stream.StreamSupport.stream(commitsJson.spliterator(), false)
                .filter(node -> strategicCommitId.equals(node.get("id").asText()))
                .findFirst()
                .orElseThrow();
        int lockedCommitVersion = lockedStrategicCommit.get("version").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(
                "Revenue Growth", lockedStrategicCommit.get("snapshotOutcomeName").asText()
        );

        UpdateCommitRequest frozenFieldUpdate = new UpdateCommitRequest(
                "Renamed after lock", null, null, null, null, null, null, null, null, null, null
        );
        mockMvc.perform(patch("/api/v1/commits/{commitId}", strategicCommitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", lockedCommitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(frozenFieldUpdate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("FIELD_FROZEN")));

        UpdateCommitRequest progressUpdate = new UpdateCommitRequest(
                null, null, null, null, null, null, null, null, null, null, "Still on track"
        );
        mockMvc.perform(patch("/api/v1/commits/{commitId}", strategicCommitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", lockedCommitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(progressUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressNotes", is("Still on track")));
    }

    @Test
    void lockPlanRejectsWhenOutcomeCannotBeResolved() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        var planJson = objectMapper.readTree(planResult.getResponse().getContentAsString());
        String planId = planJson.get("id").asText();

        CreateCommitRequest unresolvedCommit = new CreateCommitRequest(
                "Ship API", null,
                "KING", "DELIVERY", UUID.randomUUID().toString(), null,
                null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unresolvedCommit)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();

        int planVersion = getVersion(planId, userId);

        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("MISSING_RCDO_OR_REASON")))
                .andExpect(jsonPath("$.error.details", hasSize(1)))
                .andExpect(jsonPath("$.error.details[0].commitId", is(commitId)));
    }

    // ── Reconciliation Lifecycle ─────────────────────────────

    @Test
    void startReconciliationFromLocked() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commit = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commit)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("RECONCILING")));
    }

    @Test
    void lateLockPathDraftToReconciling() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commit = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commit)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("RECONCILING")))
                .andExpect(jsonPath("$.lockType", is("LATE_LOCK")))
                .andExpect(jsonPath("$.lockedAt", notNullValue()));
    }

    @Test
    void updateActualInReconcilingState() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();

        int planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Get commit version (it may have been bumped)
        MvcResult commitRefresh = mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andReturn();
        var commitsJson = objectMapper.readTree(commitRefresh.getResponse().getContentAsString());
        int commitVersion = commitsJson.get(0).get("version").asInt();

        UpdateActualRequest actualReq = new UpdateActualRequest(
                "Feature was shipped", "DONE", null, 480
        , null);
        mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", commitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionStatus", is("DONE")))
                .andExpect(jsonPath("$.actualResult", is("Feature was shipped")))
                .andExpect(jsonPath("$.timeSpent", is(480)));
    }

    @Test
    void submitReconciliationRequiresDeltaReasonForNonDoneCommits() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();

        int planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        MvcResult commitsResult = mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andReturn();
        int commitVersion = objectMapper.readTree(commitsResult.getResponse().getContentAsString())
                .get(0)
                .get("version")
                .asInt();

        UpdateActualRequest actualReq = new UpdateActualRequest(
                "Could not complete", "DROPPED", null, 60
        , null);
        mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", commitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualReq)))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/submit-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("MISSING_DELTA_REASON")));
    }

    @Test
    void fullReconciliationAndReviewLifecycle() throws Exception {
        UUID userId = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(userId);

        // Plan should be RECONCILED with REVIEW_PENDING
        mockMvc.perform(get("/api/v1/plans/{planId}", ctx.planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", ctx.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("RECONCILED")))
                .andExpect(jsonPath("$.reviewStatus", is("REVIEW_PENDING")));

        // Manager approves
        CreateReviewRequest review = new CreateReviewRequest("APPROVED", "Excellent work!");
        mockMvc.perform(post("/api/v1/plans/{planId}/review", ctx.planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("APPROVED")));

        mockMvc.perform(get("/api/v1/plans/{planId}", ctx.planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", ctx.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus", is("APPROVED")));
    }

    @Test
    void changesRequestedRevertsToReconciling() throws Exception {
        UUID userId = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(userId);

        CreateReviewRequest review = new CreateReviewRequest(
                "CHANGES_REQUESTED", "Please add delta reasons"
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/review", ctx.planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("CHANGES_REQUESTED")));

        mockMvc.perform(get("/api/v1/plans/{planId}", ctx.planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", ctx.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("RECONCILING")))
                .andExpect(jsonPath("$.reviewStatus", is("CHANGES_REQUESTED")));
    }

    @Test
    void carryForwardClonesCommitsAndBlocksChangesRequested() throws Exception {
        UUID userId = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(userId);

        // Carry forward the first commit
        CarryForwardRequest cfRequest = new CarryForwardRequest(List.of(ctx.commitId));
        mockMvc.perform(post("/api/v1/plans/{planId}/carry-forward", ctx.planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", ctx.planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cfRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("CARRY_FORWARD")))
                .andExpect(jsonPath("$.carryForwardExecutedAt", notNullValue()));

        // Verify next week plan was created with carried commits
        String nextWeekStart = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1).toString();
        mockMvc.perform(get("/api/v1/weeks/{weekStart}/plans/me", nextWeekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));

        // Manager cannot request changes after carry-forward
        CreateReviewRequest review = new CreateReviewRequest(
                "CHANGES_REQUESTED", "Too late"
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/review", ctx.planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CARRY_FORWARD_ALREADY_EXECUTED")));

        // But manager can still approve
        CreateReviewRequest approveReview = new CreateReviewRequest(
                "APPROVED", "Approved post carry-forward"
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/review", ctx.planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReview)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("APPROVED")));
    }

    // ── Owner Authorization (403 FORBIDDEN) ──────────────────

    /**
     * Helper: creates a DRAFT plan owned by ownerUserId with one commit, and returns the planId.
     */
    private String createDraftPlanWithCommit(UUID ownerUserId) throws Exception {
        String weekStart = currentMonday();
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", ownerUserId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    void wrongUserCannotCreateCommitOnAnotherUsersPlan() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String planId = createDraftPlanWithCommit(owner);

        CreateCommitRequest req = new CreateCommitRequest(
                "Injected commit", null, "KING", "DELIVERY",
                null, "Non-strategic", null, null, null, null
        );

        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotLockAnotherUsersPlan() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        // Create plan and add valid commits as owner
        String weekStart = currentMonday();
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest king = new CreateCommitRequest(
                "Legit commit", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", 0.9, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, owner);

        // Attacker tries to lock
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotLockAnotherUsersPlanEvenWithStaleVersion() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String weekStart = currentMonday();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest king = new CreateCommitRequest(
                "Legit commit", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", 0.9, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", 999)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotUpdateCommitOnAnotherUsersPlan() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String planId = createDraftPlanWithCommit(owner);

        // Owner adds a commit
        CreateCommitRequest req = new CreateCommitRequest(
                "Owner task", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();
        int commitVersion = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("version").asInt();

        // Attacker tries to update
        UpdateCommitRequest updateReq = new UpdateCommitRequest(
                "Hijacked title", null, null, null, null, null, null, null, null, null, null
        );
        mockMvc.perform(patch("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", commitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotDeleteCommitOnAnotherUsersPlan() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String planId = createDraftPlanWithCommit(owner);

        // Owner adds a commit
        CreateCommitRequest req = new CreateCommitRequest(
                "Owner task", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();

        // Attacker tries to delete
        mockMvc.perform(delete("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotStartReconciliationOnAnotherUsersPlan() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        String weekStart = currentMonday();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest king = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, owner);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, owner);

        // Attacker tries to start reconciliation
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotSubmitReconciliationOnAnotherUsersPlan() throws Exception {
        UUID attacker = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(owner); // puts plan in RECONCILED state

        // Re-enter RECONCILING via review changes_requested path: but we need
        // to test submit-reconciliation too. Let's create a fresh plan in RECONCILING state.
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks(1).toString();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest king = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, owner);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, owner);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, owner);

        // Attacker tries to submit reconciliation
        mockMvc.perform(post("/api/v1/plans/{planId}/submit-reconciliation", planId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void wrongUserCannotCarryForwardAnotherUsersPlan() throws Exception {
        UUID attacker = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(owner);

        CarryForwardRequest cfRequest = new CarryForwardRequest(List.of(ctx.commitId));
        mockMvc.perform(post("/api/v1/plans/{planId}/carry-forward", ctx.planId)
                        .header("X-User-Id", attacker)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", ctx.planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cfRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    // ── Bean Validation (422 VALIDATION_ERROR) ───────────────

    @Test
    void createCommitWithEmptyTitleReturns422() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // title is blank — @NotBlank should fire
        String blankTitleBody = """
                {"title": "", "chessPriority": "QUEEN", "category": "OPERATIONS",
                 "nonStrategicReason": "admin"}
                """;
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankTitleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("title")));
    }

    @Test
    void createCommitWithTitleExceeding500CharsReturns422() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // title is 501 characters — @Size(max=500) should fire
        String longTitle = "A".repeat(501);
        String longTitleBody = "{\"title\": \"" + longTitle + "\"}";
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(longTitleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("title")));
    }

    @Test
    void carryForwardWithEmptyCommitIdsReturns422() throws Exception {
        UUID owner = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(owner);

        // empty list — @NotEmpty should fire
        CarryForwardRequest emptyRequest = new CarryForwardRequest(List.of());
        mockMvc.perform(post("/api/v1/plans/{planId}/carry-forward", ctx.planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", ctx.planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("commitIds")));
    }

    @Test
    void updateCommitWithEmptyTitleReturns422() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest req = new CreateCommitRequest(
                "Original title", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();
        int version = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("version").asInt();

        // title is empty string — validation should fire
        String emptyTitleBody = """
                {"title": ""}
                """;
        mockMvc.perform(patch("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyTitleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("title")));
    }

    @Test
    void updateCommitWithBlankTitleReturns422() throws Exception {
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest req = new CreateCommitRequest(
                "Original title", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();
        int version = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("version").asInt();

        String blankTitleBody = """
                {"title": "   "}
                """;
        mockMvc.perform(patch("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", version)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankTitleBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("title")));
    }

    @Test
    void submitReviewWithInvalidDecisionReturns422() throws Exception {
        UUID userId = UUID.randomUUID();
        PlanTestContext ctx = createReconciledPlan(userId);

        String invalidReviewBody = """
                {"decision": "REJECTED", "comments": "Nope"}
                """;
        mockMvc.perform(post("/api/v1/plans/{planId}/review", ctx.planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidReviewBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("decision")));
    }

    // ── Concurrent Optimistic Lock Conflict ──────────────────

    @Test
    void secondLockWithStaleVersionGets409Conflict() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Add valid commits (KING + QUEEN)
        CreateCommitRequest king = new CreateCommitRequest(
                "Concurrent feature", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Feature live", 0.9, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        CreateCommitRequest queen = new CreateCommitRequest(
                "Support work", null, "QUEEN", "OPERATIONS",
                null, "Admin support", null, null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queen)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, userId);

        // First lock succeeds — version advances
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Second lock with the now-stale version → 409 optimistic-lock conflict
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)          // stale version
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")))
                .andExpect(jsonPath("$.error.details[0].currentVersion", notNullValue()));
    }

    // ── Cross-Org Isolation ───────────────────────────────────

    @Test
    void planCreatedInOrgAIsNotVisibleFromOrgB() throws Exception {
        UUID orgA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID orgB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        UUID userId = UUID.randomUUID();
        String weekStart = currentMonday();

        // Create plan in org A
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", orgA))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Reading the plan from org B → 404 (not found in that org)
        mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("X-Org-Id", orgB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    @Test
    void userInOrgBCannotMutateCommitOnOrgAPlan() throws Exception {
        UUID orgA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000011");
        UUID orgB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000012");
        UUID userInOrgA = UUID.randomUUID();
        UUID userInOrgB = UUID.randomUUID();
        String weekStart = currentMonday();

        // Create plan in org A and add a commit
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userInOrgA)
                        .header("X-Org-Id", orgA))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest req = new CreateCommitRequest(
                "Org-A task", null, null, null, null, null, null, null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userInOrgA)
                        .header("X-Org-Id", orgA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();
        int commitVersion = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("version").asInt();

        // User from org B attempts to update the commit — plan is not found in org B scope → 404
        UpdateCommitRequest update = new UpdateCommitRequest(
                "Hijacked title", null, null, null, null, null, null, null, null, null, null
        );
        mockMvc.perform(patch("/api/v1/commits/{commitId}", commitId)
                        .header("X-User-Id", userInOrgB)
                        .header("X-Org-Id", orgB)
                        .header("If-Match", commitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));
    }

    // ── Missing Required Headers ──────────────────────────────

    @Test
    void missingOrgIdHeaderReturns401() throws Exception {
        // No X-Org-Id → DevRequestAuthenticator throws AuthenticationException → 401
        mockMvc.perform(get("/api/v1/weeks/{weekStart}/plans/me", currentMonday())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIfMatchHeaderOnLockReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String weekStart = currentMonday();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Lock without If-Match header → MissingRequestHeaderException → 400 VALIDATION_ERROR
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].header", is("If-Match")));
    }

    @Test
    void missingIfMatchHeaderOnStartReconciliationReturns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String weekStart = currentMonday();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Start-reconciliation without If-Match → 400 VALIDATION_ERROR
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].header", is("If-Match")));
    }

    // ── Team Dashboard: no direct reports → empty, not error ─

    @Test
    void teamSummaryWithNoDirectReportsReturnsEmptyNotError() throws Exception {
        String weekStart = currentMonday();
        // MANAGER_ID has no direct reports registered in orgGraphClient (cleared in @AfterEach)

        mockMvc.perform(get("/api/v1/weeks/{weekStart}/team/summary", weekStart)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void rcdoRollupWithNoDirectReportsReturnsEmptyNotError() throws Exception {
        String weekStart = currentMonday();

        mockMvc.perform(get("/api/v1/weeks/{weekStart}/team/rcdo-rollup", weekStart)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.nonStrategicCount", is(0)));
    }

    // ── RCDO Cache Staleness → lock blocked ──────────────────

    @Test
    void lockPlanRejectedWhenRcdoCacheIsStale() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Add valid commits (KING + QUEEN) so chess rules pass
        CreateCommitRequest king = new CreateCommitRequest(
                "Ship API", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "API ready", 0.9, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        CreateCommitRequest queen = new CreateCommitRequest(
                "Support work", null, "QUEEN", "OPERATIONS",
                null, "Admin support", null, null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queen)))
                .andExpect(status().isCreated());

        // Mark the RCDO cache as stale (90 minutes old — beyond the 60-minute threshold)
        rcdoClient.markStale(ORG_ID);

        int planVersion = getVersion(planId, userId);

        // Lock attempt must be rejected with RCDO_VALIDATION_STALE
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("RCDO_VALIDATION_STALE")));
    }

    // ── Manager Review on Non-Reviewable State → 409 ─────────

    @Test
    void managerReviewOnDraftPlanReturns409() throws Exception {
        UUID owner = UUID.randomUUID();
        String weekStart = currentMonday();
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(owner));

        // Create plan in DRAFT (not yet reconciled)
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Manager attempts review on DRAFT plan → 409 CONFLICT
        CreateReviewRequest review = new CreateReviewRequest("APPROVED", "Great work");
        mockMvc.perform(post("/api/v1/plans/{planId}/review", planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")));
    }

    @Test
    void managerReviewOnLockedPlanReturns409() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        UUID owner = UUID.randomUUID();
        String weekStart = currentMonday();
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(owner));

        // Create + commit + lock plan
        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest king = new CreateCommitRequest(
                "Lock task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Done", 0.9, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        CreateCommitRequest queen = new CreateCommitRequest(
                "Support work", null, "QUEEN", "OPERATIONS",
                null, "Admin support", null, null, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queen)))
                .andExpect(status().isCreated());

        int planVersion = getVersion(planId, owner);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", owner)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Manager attempts review on LOCKED plan → 409 CONFLICT
        CreateReviewRequest review = new CreateReviewRequest("APPROVED", "Good job");
        mockMvc.perform(post("/api/v1/plans/{planId}/review", planId)
                        .header("X-User-Id", MANAGER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("CONFLICT")));
    }

    // ── Notification Unread Endpoint ──────────────────────────

    @Test
    void notificationUnreadEndpointReturnsEmptyListWhenNoNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread")
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void notificationUnreadEndpointReturnsOnlyCurrentUsersUnreadNotificationsWithExpectedShape()
            throws Exception {
        UUID currentUser = UUID.randomUUID();
        NotificationEntity expected = notificationRepository.save(new NotificationEntity(
                ORG_ID,
                currentUser,
                "RECONCILIATION_SUBMITTED",
                java.util.Map.of("planId", UUID.randomUUID().toString(), "ownerUserName", "Alice")
        ));
        notificationRepository.save(new NotificationEntity(
                ORG_ID,
                UUID.randomUUID(),
                "PLAN_LOCKED",
                java.util.Map.of("planId", UUID.randomUUID().toString())
        ));
        notificationRepository.save(new NotificationEntity(
                UUID.fromString("99999999-0000-0000-0000-000000000999"),
                currentUser,
                "PLAN_LOCKED",
                java.util.Map.of("planId", UUID.randomUUID().toString())
        ));

        mockMvc.perform(get("/api/v1/notifications/unread")
                        .header("X-User-Id", currentUser)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(expected.getId().toString())))
                .andExpect(jsonPath("$[0].type", is("RECONCILIATION_SUBMITTED")))
                .andExpect(jsonPath("$[0].payload.planId", is(expected.getPayload().get("planId"))))
                .andExpect(jsonPath("$[0].payload.ownerUserName", is("Alice")))
                .andExpect(jsonPath("$[0].read", is(false)))
                .andExpect(jsonPath("$[0].createdAt", notNullValue()));
    }

    // ── updateActualWithInvalidCompletionStatusReturns422 ──────

    @Test
    void updateActualWithInvalidCompletionStatusReturns422() throws Exception {
        UUID outcomeId = seedRcdoOutcome();
        String weekStart = currentMonday();
        UUID userId = UUID.randomUUID();

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        CreateCommitRequest commitReq = new CreateCommitRequest(
                "Task", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Result", null, null, null
        );
        MvcResult commitResult = mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String commitId = objectMapper.readTree(commitResult.getResponse().getContentAsString())
                .get("id").asText();

        int planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        planVersion = getVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", planVersion)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        MvcResult commitRefresh = mockMvc.perform(get("/api/v1/plans/{planId}/commits", planId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isOk())
                .andReturn();
        int commitVersion = objectMapper.readTree(commitRefresh.getResponse().getContentAsString())
                .get(0)
                .get("version")
                .asInt();

        String invalidActualBody = """
                {"actualResult": "Feature was shipped", "completionStatus": "ALMOST_DONE"}
                """;
        mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", commitId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", commitVersion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidActualBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.details[0].field", is("completionStatus")));
    }
}
