package com.weekly.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.auth.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdempotencyKeyFilter}.
 *
 * <p>Covers: replay, conflict (key reuse with different hash), missing header,
 * passthrough for non-lifecycle paths, and successful storage on first use.
 */
class IdempotencyKeyFilterTest {

    private static final UUID ORG_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private IdempotencyKeyService service;
    private IdempotencyKeyFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = mock(IdempotencyKeyService.class);
        objectMapper = new ObjectMapper();
        filter = new IdempotencyKeyFilter(service, objectMapper);

        UserPrincipal principal = new UserPrincipal(USER_ID, ORG_ID, Set.of("MEMBER"));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Path matching ──────────────────────────────────────────────────────────

    @Nested
    class PathMatching {

        @Test
        void getRequestPassesThrough() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET",
                    "/api/v1/plans/" + PLAN_ID + "/lock");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        }

        @Test
        void nonLifecyclePostPassesThrough() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST",
                    "/api/v1/plans/" + PLAN_ID + "/review");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        void createCommitPostPassesThrough() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST",
                    "/api/v1/plans/" + PLAN_ID + "/commits");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        }
    }

    // ── Missing / invalid header ───────────────────────────────────────────────

    @Nested
    class MissingHeader {

        @Test
        void returns400WhenHeaderAbsent() throws Exception {
            MockHttpServletRequest request = lockRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(400, response.getStatus());
            assertTrue(response.getContentAsString().contains("MISSING_IDEMPOTENCY_KEY"));
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        void returns400WhenHeaderIsBlank() throws Exception {
            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER, "   ");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(400, response.getStatus());
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        void returns400WhenHeaderIsNotAUuid() throws Exception {
            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER, "not-a-uuid");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(400, response.getStatus());
            assertTrue(response.getContentAsString().contains("MISSING_IDEMPOTENCY_KEY"));
            verify(chain, never()).doFilter(any(), any());
        }
    }

    // ── Replay scenario ────────────────────────────────────────────────────────

    @Nested
    class ReplayScenario {

        @Test
        void replaysStoredResponseWhenKeyExistsWithSameHash() throws Exception {
            byte[] emptyBody = new byte[0];
            String hash = requestHash("POST", "/api/v1/plans/" + PLAN_ID + "/lock", null, emptyBody);

            Map<String, Object> cachedBody = Map.of(
                    "plan", Map.of("id", PLAN_ID.toString(), "state", "LOCKED"));
            IdempotencyKeyEntity stored = new IdempotencyKeyEntity(
                    ORG_ID, IDEMPOTENCY_KEY, USER_ID,
                    "/api/v1/plans/" + PLAN_ID + "/lock",
                    hash, 200, cachedBody
            );
            when(service.findExisting(ORG_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(stored));

            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
            assertEquals("true", response.getHeader("X-Idempotency-Replay"));
            assertTrue(response.getContentAsString().contains("LOCKED"));
            verify(chain, never()).doFilter(any(), any());
            verify(service, never()).store(any(), any(), any(), anyString(), anyString(),
                    anyInt(), any());
        }
    }

    // ── Key reuse conflict ─────────────────────────────────────────────────────

    @Nested
    class KeyReuseConflict {

        @Test
        void returns422WhenKeyExistsWithDifferentHash() throws Exception {
            String differentHash =
                    "0000000000000000000000000000000000000000000000000000000000000000";
            IdempotencyKeyEntity stored = new IdempotencyKeyEntity(
                    ORG_ID, IDEMPOTENCY_KEY, USER_ID,
                    "/api/v1/plans/" + PLAN_ID + "/lock",
                    differentHash, 200, Map.of("plan", Map.of("state", "LOCKED"))
            );
            when(service.findExisting(ORG_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(stored));

            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            // Non-empty body so its hash differs from differentHash
            request.setContent("{\"note\":\"retry\"}".getBytes(StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(422, response.getStatus());
            assertTrue(response.getContentAsString().contains("IDEMPOTENCY_KEY_REUSE"));
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        void returns422WhenKeyIsReusedOnDifferentEndpointWithSameBody() throws Exception {
            byte[] emptyBody = new byte[0];
            String lockHash = requestHash("POST", "/api/v1/plans/" + PLAN_ID + "/lock", null,
                    emptyBody);
            IdempotencyKeyEntity stored = new IdempotencyKeyEntity(
                    ORG_ID, IDEMPOTENCY_KEY, USER_ID,
                    "/api/v1/plans/" + PLAN_ID + "/lock",
                    lockHash, 200, Map.of("plan", Map.of("state", "LOCKED"))
            );
            when(service.findExisting(ORG_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.of(stored));

            MockHttpServletRequest request = new MockHttpServletRequest("POST",
                    "/api/v1/plans/" + PLAN_ID + "/start-reconciliation");
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(422, response.getStatus());
            assertTrue(response.getContentAsString().contains("IDEMPOTENCY_KEY_REUSE"));
            verify(chain, never()).doFilter(any(), any());
        }
    }

    // ── First-use: store response ──────────────────────────────────────────────

    @Nested
    class FirstUse {

        @Test
        void proceedsAndStoresResponseWhenKeyIsNew() throws Exception {
            when(service.findExisting(ORG_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                HttpServletResponse httpRes = (HttpServletResponse) res;
                httpRes.setStatus(200);
                httpRes.setContentType("application/json");
                httpRes.getWriter().write("{\"plan\":{\"state\":\"LOCKED\"}}");
            };

            filter.doFilter(request, response, chain);

            assertFalse(response.getContentAsString().isBlank());
            verify(service).store(
                    any(UUID.class), any(UUID.class), any(UUID.class),
                    anyString(), anyString(), anyInt(), any()
            );
        }

        @Test
        void proceedsAndStoresResponseForCarryForwardWithBody() throws Exception {
            when(service.findExisting(ORG_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

            MockHttpServletRequest request = new MockHttpServletRequest("POST",
                    "/api/v1/plans/" + PLAN_ID + "/carry-forward");
            String body = "{\"commitIds\":[\"" + UUID.randomUUID() + "\"]}";
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                byte[] downstream = req.getInputStream().readAllBytes();
                assertEquals(body, new String(downstream, StandardCharsets.UTF_8));
                ((HttpServletResponse) res).setStatus(200);
                res.getWriter().write("{\"plan\":{\"state\":\"RECONCILED\"}}");
            };

            filter.doFilter(request, response, chain);

            verify(service).store(any(), any(), any(), anyString(), anyString(), anyInt(), any());
        }

        @Test
        void doesNotStoreResponseWhenDownstreamThrows() throws Exception {
            when(service.findExisting(ORG_ID, IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain chain = (req, res) -> {
                throw new IllegalStateException("boom");
            };

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> filter.doFilter(request, response, chain));

            assertEquals("boom", thrown.getMessage());
            verify(service, never()).store(any(), any(), any(), anyString(), anyString(),
                    anyInt(), any());
        }
    }

    // ── No authentication ──────────────────────────────────────────────────────

    @Nested
    class NoAuthentication {

        @Test
        void passesThroughWhenNoAuthenticationInContext() throws Exception {
            SecurityContextHolder.clearContext();

            MockHttpServletRequest request = lockRequest();
            request.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER,
                    IDEMPOTENCY_KEY.toString());
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest lockRequest() {
        return new MockHttpServletRequest("POST",
                "/api/v1/plans/" + PLAN_ID + "/lock");
    }

    private static String requestHash(
            String method,
            String path,
            String query,
            byte[] body
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(method.getBytes(StandardCharsets.UTF_8));
            digest.update("\n".getBytes(StandardCharsets.UTF_8));
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update("\n".getBytes(StandardCharsets.UTF_8));
            digest.update((query == null ? "" : query).getBytes(StandardCharsets.UTF_8));
            digest.update("\n".getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
