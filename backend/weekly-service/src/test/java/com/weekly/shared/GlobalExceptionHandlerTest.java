package com.weekly.shared;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.InMemoryOrgGraphClient;
import com.weekly.plan.dto.CarryForwardRequest;
import com.weekly.plan.dto.CreateCommitRequest;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
 * Integration tests for {@link GlobalExceptionHandler}.
 *
 * <p>Each test exercises a specific exception path end-to-end through MockMvc,
 * verifying that the handler produces a well-formed {@link ApiErrorResponse} envelope
 * with the correct HTTP status code and error code — without leaking stack traces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryRcdoClient rcdoClient;

    @Autowired
    private InMemoryOrgGraphClient orgGraphClient;

    private static final UUID ORG_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @AfterEach
    void clearCaches() {
        rcdoClient.clear();
        orgGraphClient.clear();
    }

    // ── (0) MethodArgumentNotValidException ────────────────

    @Test
    void beanValidationOutsidePlanControllersReturns422WithValidationDetails() throws Exception {
        mockMvc.perform(post("/api/v1/test/errors/validation")
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", is("Request validation failed")))
                .andExpect(jsonPath("$.error.details[0].field", is("name")));
    }

    // ── (1) HttpMessageNotReadableException ─────────────────

    @Test
    void malformedJsonBodyReturns400WithValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", UUID.randomUUID())
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", notNullValue()))
                .andExpect(jsonPath("$.error.details", notNullValue()));
    }

    @Test
    void emptyBodyWhenJsonRequiredReturns400() throws Exception {
        // PATCH /commits/{id} expects a JSON body; sending none should be unreadable
        mockMvc.perform(patch("/api/v1/commits/{commitId}", UUID.randomUUID())
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", 0)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    // ── (2) MissingRequestHeaderException ───────────────────

    @Test
    void missingIfMatchOnPatchCommitReturns400WithHeaderName() throws Exception {
        // Omit the required If-Match header on a PATCH request
        mockMvc.perform(patch("/api/v1/commits/{commitId}", UUID.randomUUID())
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"updated\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("If-Match")))
                .andExpect(jsonPath("$.error.details[0].header", is("If-Match")));
    }

    @Test
    void missingIfMatchOnLockPlanReturns400() throws Exception {
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();

        // Create a plan first so the planId is valid and the rejection is from missing If-Match
        MvcResult createResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", UUID.randomUUID())
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                // No If-Match header
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("If-Match")));
    }

    // ── (3) DateTimeParseException ───────────────────────────

    @Test
    void invalidDateInWeekStartCreatePlanReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", "not-a-date")
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INVALID_WEEK_START")))
                .andExpect(jsonPath("$.error.message", containsString("not-a-date")));
    }

    @Test
    void invalidDateInWeekStartGetMyPlanReturns422() throws Exception {
        mockMvc.perform(get("/api/v1/weeks/{weekStart}/plans/me", "2024-13-99")
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("INVALID_WEEK_START")));
    }

    // ── (4) MethodArgumentTypeMismatchException ──────────────

    @Test
    void nonUuidPlanIdReturns422WithValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}", "not-a-uuid")
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("planId")));
    }

    @Test
    void nonUuidCommitIdReturns422WithValidationError() throws Exception {
        mockMvc.perform(patch("/api/v1/commits/{commitId}", "bad-id")
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"updated\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", containsString("commitId")));
    }

    // ── (5) IllegalArgumentException (UUID.fromString in controller) ──

    @Test
    void carryForwardWithInvalidCommitUuidReturns422() throws Exception {
        // Build a RECONCILED plan so carry-forward reaches the UUID.fromString call
        UUID outcomeId = seedRcdoOutcome();
        UUID userId = UUID.randomUUID();
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(userId));

        MvcResult planResult = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn();
        String planId = objectMapper.readTree(planResult.getResponse().getContentAsString())
                .get("id").asText();

        // Add commit, lock, start reconciliation, fill actuals, submit
        CreateCommitRequest king = new CreateCommitRequest(
                "Ship feature", null, "KING", "DELIVERY",
                outcomeId.toString(), null, "Feature live", 0.9, null, null
        );
        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(king)))
                .andExpect(status().isCreated());

        int v = getPlanVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/lock", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", v)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        v = getPlanVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/start-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", v)
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
            mockMvc.perform(patch("/api/v1/commits/{commitId}/actual", cId)
                            .header("X-Org-Id", ORG_ID)
                            .header("X-User-Id", userId)
                            .header("If-Match", cVersion)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualResult\":\"Done\",\"completionStatus\":\"DONE\"}"))
                    .andExpect(status().isOk());
        }

        v = getPlanVersion(planId, userId);
        mockMvc.perform(post("/api/v1/plans/{planId}/submit-reconciliation", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", v)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        // Now attempt carry-forward with an invalid UUID string in commitIds
        v = getPlanVersion(planId, userId);
        CarryForwardRequest cfRequest = new CarryForwardRequest(List.of("not-a-valid-uuid"));
        mockMvc.perform(post("/api/v1/plans/{planId}/carry-forward", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .header("If-Match", v)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cfRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    // ── (6) Generic Exception (catch-all) ────────────────────

    @Test
    void unhandledExceptionOutsidePlanControllersReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test/errors/boom")
                        .header("X-User-Id", USER_ID)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code", is("INTERNAL_SERVER_ERROR")))
                .andExpect(jsonPath("$.error.message", is("An unexpected error occurred")))
                .andExpect(content().string(not(containsString("Synthetic failure"))));
    }

    @Test
    void handlerDirectlyReturns500ForUnhandledException() {
        // Test the handler method directly to verify the 500 envelope format
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException unexpected = new RuntimeException("database exploded");

        var response = handler.handleGeneric(unexpected);

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().error().code());
        assertEquals("An unexpected error occurred", response.getBody().error().message());
    }

    // ── Handler unit tests ────────────────────────────────────

    @Test
    void handlerReturnsCorrectEnvelopeForDateTimeParseException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DateTimeParseException ex = new DateTimeParseException("bad date", "xyz", 0);

        var response = handler.handleDateTimeParse(ex);

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_WEEK_START", response.getBody().error().code());
        assertNotNull(response.getBody().error().message());
    }

    @Test
    void handlerReturnsCorrectEnvelopeForIllegalArgumentException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IllegalArgumentException ex = new IllegalArgumentException("Invalid UUID string: bad");

        var response = handler.handleIllegalArgument(ex);

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().error().code());
        assertEquals("Invalid UUID string: bad", response.getBody().error().message());
    }

    @Test
    void handlerReturnsCorrectEnvelopeForIllegalArgumentExceptionWithNullMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        var response = handler.handleIllegalArgument(ex);

        assertEquals(422, response.getStatusCode().value());
        assertEquals("Invalid argument", response.getBody().error().message());
    }

    // ── Helpers ──────────────────────────────────────────────

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

    private int getPlanVersion(String planId, UUID userId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/plans/{planId}", planId)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("version").asInt();
    }
}
