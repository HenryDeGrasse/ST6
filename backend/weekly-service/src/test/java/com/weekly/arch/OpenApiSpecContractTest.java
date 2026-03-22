package com.weekly.arch;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contract-level test asserting the OpenAPI spec paths match Spring controller mappings.
 *
 * <p>Validates three categories of contract alignment:
 * <ol>
 *   <li><b>Path coverage</b> — Every path+method combination in {@code contracts/openapi.yaml}
 *       has a corresponding Spring {@code @RequestMapping} handler registered.</li>
 *   <li><b>Required header enforcement</b> — Lifecycle mutation endpoints return HTTP 400
 *       when the required {@code If-Match} or {@code Idempotency-Key} header is absent,
 *       confirming the documented contract is enforced at runtime.</li>
 *   <li><b>Response schema</b> — Key endpoints return JSON bodies containing all fields
 *       required by the OpenAPI schema (e.g. {@code WeeklyPlan}, {@code WeeklyCommit}).</li>
 * </ol>
 *
 * <p>This test focuses on the lifecycle endpoints (lock, start-reconciliation,
 * submit-reconciliation, carry-forward) and CRUD endpoints (create/list/update/delete commit),
 * as described in the assignment spec §11.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSpecContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The primary RequestMappingHandlerMapping bean created by Spring MVC.
     * Provides access to all registered controller path patterns.
     */
    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RANDOM_PLAN_ID = UUID.randomUUID();
    private static final UUID RANDOM_COMMIT_ID = UUID.randomUUID();

    /**
     * All path+method combinations from {@code contracts/openapi.yaml}.
     *
     * <p>Each entry is {@code "METHOD /path"} where the path does NOT include the
     * {@code /api/v1} prefix (Spring controllers add that prefix; the OpenAPI spec omits it
     * because the server URL defines the base path).
     *
     * <p>Count: 41 operations across the currently documented controllers.
     */
    private static final Set<String> EXPECTED_OPENAPI_OPERATIONS = Set.of(
            // Plans
            "POST /weeks/{weekStart}/plans",
            "GET /weeks/{weekStart}/plans/me",
            "GET /plans/{planId}",
            "GET /weeks/{weekStart}/plans/{userId}",
            "GET /weeks/{weekStart}/plans/{userId}/commits",
            // Lifecycle mutations
            "POST /plans/{planId}/lock",
            "POST /plans/{planId}/start-reconciliation",
            "POST /plans/{planId}/submit-reconciliation",
            "POST /plans/{planId}/carry-forward",
            // Commits (two HTTP methods on same path)
            "GET /plans/{planId}/commits",
            "POST /plans/{planId}/commits",
            "PATCH /commits/{commitId}",
            "DELETE /commits/{commitId}",
            "PATCH /commits/{commitId}/actual",
            // Manager dashboard
            "GET /weeks/{weekStart}/team/summary",
            "GET /weeks/{weekStart}/team/rcdo-rollup",
            // Notifications
            "GET /notifications/unread",
            "POST /notifications/{notificationId}/read",
            "POST /notifications/read-all",
            // Review
            "POST /plans/{planId}/review",
            // RCDO
            "GET /rcdo/tree",
            "GET /rcdo/search",
            // AI
            "POST /ai/suggest-rcdo",
            "POST /ai/draft-reconciliation",
            "POST /ai/manager-insights",
            "POST /ai/plan-quality-check",
            // "Start My Week" — draft from history (Wave 2)
            "POST /plans/draft-from-history",
            // Next-work suggestions — Wave 2, Step 9
            "POST /ai/suggest-next-work",
            "POST /ai/suggestion-feedback",
            // Trends
            "GET /users/me/trends",
            // Quick Daily Check-In — Wave 2, Step 11
            "POST /commits/{commitId}/check-in",
            "GET /commits/{commitId}/check-ins",
            // Admin — org policy / digest config (Wave 3, Step 17)
            "GET /admin/org-policy",
            "PATCH /admin/org-policy/digest",
            // Phase 5 forecasting
            "GET /outcomes/forecasts",
            "GET /outcomes/{outcomeId}/forecast",
            // Phase 5 planning copilot
            "POST /ai/team-plan-suggestion",
            "POST /ai/team-plan-suggestion/apply",
            // Phase 5 executive dashboard
            "GET /executive/strategic-health",
            "POST /ai/executive-briefing",
            // Health
            "GET /health"
    );

    // ─── 1. Path Coverage ──────────────────────────────────────────────────────

    /**
     * Asserts that every path+method in the OpenAPI spec has a registered Spring handler.
     *
     * <p>Mechanism: collects all {@link RequestMappingInfo} entries from the primary
     * handler mapping, normalises each to {@code "METHOD /path"} by stripping the
     * {@code /api/v1} prefix, and asserts each expected operation is present.
     *
     * <p>If this test fails, it means a path was added to the OpenAPI spec but the
     * corresponding controller method was not implemented.
     */
    @Test
    void allOpenApiPathsHaveSpringControllerMapping() {
        Set<String> registeredOperations = new HashSet<>();

        for (Map.Entry<RequestMappingInfo, ?> entry :
                requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();

            Set<String> methods = info.getMethodsCondition().getMethods()
                    .stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            Set<String> patterns = info.getPatternValues();

            for (String method : methods) {
                for (String pattern : patterns) {
                    // Strip the /api/v1 prefix so patterns match OpenAPI path keys
                    String normalizedPath = pattern.startsWith("/api/v1")
                            ? pattern.substring("/api/v1".length())
                            : pattern;
                    registeredOperations.add(method + " " + normalizedPath);
                }
            }
        }

        for (String expected : EXPECTED_OPENAPI_OPERATIONS) {
            assertTrue(
                    registeredOperations.contains(expected),
                    "OpenAPI operation has no Spring controller mapping: '" + expected
                    + "'. Registered operations: " + registeredOperations
            );
        }
    }

    /**
     * Asserts that the number of expected OpenAPI operations matches the hardcoded set,
     * acting as a sentinel for unintentional changes to EXPECTED_OPENAPI_OPERATIONS.
     */
    @Test
    void expectedOperationCountMatchesOpenApiSpec() {
        // Update this sentinel whenever the committed OpenAPI path set changes.
        // Recent additions include Phase 5 forecasting, planning-copilot,
        // and executive dashboard / briefing surfaces.
        int expectedCount = 41;
        assertTrue(
                EXPECTED_OPENAPI_OPERATIONS.size() == expectedCount,
                "Expected " + expectedCount + " OpenAPI operations but found "
                + EXPECTED_OPENAPI_OPERATIONS.size()
                + ". Update EXPECTED_OPENAPI_OPERATIONS and this count when the spec changes."
        );
    }

    // ─── 2. If-Match Header Enforcement ───────────────────────────────────────

    /**
     * Asserts that lifecycle mutation endpoints and commit PATCH endpoints return HTTP 400
     * when the required {@code If-Match} header is absent.
     *
     * <p>The spec documents {@code If-Match} as {@code required: true} for these endpoints.
     * Spring enforces this via {@code @RequestHeader("If-Match")}; the
     * {@link com.weekly.shared.GlobalExceptionHandler} converts the resulting
     * {@code MissingRequestHeaderException} to a 400 with {@code VALIDATION_ERROR}.
     *
     * <p>The {@code Idempotency-Key} header is provided so the idempotency filter does not
     * short-circuit the request before the controller can enforce {@code If-Match}.
     */
    @ParameterizedTest(name = "POST {0} requires If-Match header")
    @ValueSource(strings = {
            "/plans/{planId}/lock",
            "/plans/{planId}/start-reconciliation",
            "/plans/{planId}/submit-reconciliation"
    })
    void lifecyclePostEndpointsRequireIfMatch(String pathTemplate) throws Exception {
        String path = "/api/v1" + pathTemplate.replace("{planId}", RANDOM_PLAN_ID.toString());

        mockMvc.perform(post(path)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        // If-Match header intentionally omitted
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("If-Match")
                ));
    }

    @Test
    void carryForwardRequiresIfMatch() throws Exception {
        String path = "/api/v1/plans/" + RANDOM_PLAN_ID + "/carry-forward";
        String body = objectMapper.writeValueAsString(Map.of("commitIds", new String[]{}));

        mockMvc.perform(post(path)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        // If-Match header intentionally omitted
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("If-Match")
                ));
    }

    @Test
    void updateCommitRequiresIfMatch() throws Exception {
        String path = "/api/v1/commits/" + RANDOM_COMMIT_ID;
        String body = "{}"; // UpdateCommitRequest — all fields optional

        mockMvc.perform(patch(path)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        // If-Match header intentionally omitted
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("If-Match")
                ));
    }

    @Test
    void updateActualRequiresIfMatch() throws Exception {
        String path = "/api/v1/commits/" + RANDOM_COMMIT_ID + "/actual";
        String body = objectMapper.writeValueAsString(
                Map.of("actualResult", "done", "completionStatus", "DONE")
        );

        mockMvc.perform(patch(path)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        // If-Match header intentionally omitted
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("If-Match")
                ));
    }

    // ─── 3. Idempotency-Key Header Enforcement ─────────────────────────────────

    /**
     * Asserts that lifecycle mutation endpoints return HTTP 400 with
     * {@code MISSING_IDEMPOTENCY_KEY} when the {@code Idempotency-Key} header is absent.
     *
     * <p>The spec documents {@code Idempotency-Key} as {@code required: true} for the four
     * lifecycle POST endpoints. The {@link com.weekly.idempotency.IdempotencyKeyFilter}
     * enforces this before the request reaches the controller.
     */
    @ParameterizedTest(name = "POST {0} requires Idempotency-Key header")
    @ValueSource(strings = {
            "/plans/{planId}/lock",
            "/plans/{planId}/start-reconciliation",
            "/plans/{planId}/submit-reconciliation",
            "/plans/{planId}/carry-forward"
    })
    void lifecycleEndpointsRequireIdempotencyKey(String pathTemplate) throws Exception {
        String path = "/api/v1" + pathTemplate.replace("{planId}", RANDOM_PLAN_ID.toString());

        mockMvc.perform(post(path)
                        .header("X-Org-Id", ORG_ID)
                        .header("X-User-Id", USER_ID)
                        .header("If-Match", 0)
                        // Idempotency-Key header intentionally omitted
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("MISSING_IDEMPOTENCY_KEY")));
    }

    // ─── 4. Response Schema Validation ─────────────────────────────────────────

    /**
     * Asserts that {@code POST /weeks/{weekStart}/plans} (createPlan) returns a JSON body
     * that contains all required fields from the OpenAPI {@code WeeklyPlan} schema:
     * {@code id, orgId, ownerUserId, weekStartDate, state, reviewStatus, version,
     * createdAt, updatedAt}.
     *
     * <p>This verifies the Java {@code WeeklyPlanResponse} record serialises every
     * required field documented in the spec.
     */
    @Test
    void createPlanResponseContainsAllRequiredWeeklyPlanFields() throws Exception {
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                // Required fields per OpenAPI WeeklyPlan schema
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.orgId", notNullValue()))
                .andExpect(jsonPath("$.ownerUserId", notNullValue()))
                .andExpect(jsonPath("$.weekStartDate", notNullValue()))
                .andExpect(jsonPath("$.state", notNullValue()))
                .andExpect(jsonPath("$.reviewStatus", notNullValue()))
                .andExpect(jsonPath("$.version", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                // Nullable optional fields must be serialised (even when null).
                // The Java record always serialises them as JSON null; the spec marks them nullable.
                // Use .value(nullValue()) because .exists() fails for JSON null values.
                .andExpect(jsonPath("$.lockType").value(nullValue()))
                .andExpect(jsonPath("$.lockedAt").value(nullValue()))
                .andExpect(jsonPath("$.carryForwardExecutedAt").value(nullValue()));
    }

    /**
     * Asserts that {@code POST /plans/{planId}/commits} (createCommit) returns a JSON body
     * that contains all required fields from the OpenAPI {@code WeeklyCommit} schema:
     * {@code id, weeklyPlanId, title, description, expectedResult, progressNotes, tags,
     * version, createdAt, updatedAt, validationErrors}.
     */
    @Test
    void createCommitResponseContainsAllRequiredWeeklyCommitFields() throws Exception {
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        UUID userId = UUID.randomUUID();

        // Create plan first
        String planBody = mockMvc.perform(post("/api/v1/weeks/{weekStart}/plans", weekStart)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String planId = objectMapper.readTree(planBody).get("id").asText();

        String commitBody = objectMapper.writeValueAsString(Map.of("title", "Contract check commit"));

        mockMvc.perform(post("/api/v1/plans/{planId}/commits", planId)
                        .header("X-User-Id", userId)
                        .header("X-Org-Id", ORG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody))
                .andExpect(status().isCreated())
                // Required fields per OpenAPI WeeklyCommit schema
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.weeklyPlanId", notNullValue()))
                .andExpect(jsonPath("$.title", is("Contract check commit")))
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.expectedResult").exists())
                .andExpect(jsonPath("$.progressNotes").exists())
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.version", notNullValue()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    /**
     * Asserts that {@code GET /api/v1/health} returns HTTP 200.
     *
     * <p>The health endpoint is documented in the spec as unauthenticated ({@code security: []}).
     * This test verifies both that the endpoint is mapped and that it responds without auth.
     */
    @Test
    void healthEndpointRespondsWith200WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }
}
