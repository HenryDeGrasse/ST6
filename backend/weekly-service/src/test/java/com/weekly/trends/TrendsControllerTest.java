package com.weekly.trends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.shared.ApiErrorResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link TrendsController}: parameter validation and delegation.
 */
class TrendsControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private TrendsService trendsService;
    private AuthenticatedUserContext authenticatedUserContext;
    private TrendsController controller;

    @BeforeEach
    void setUp() {
        trendsService = mock(TrendsService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );
        controller = new TrendsController(trendsService, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TrendsResponse stubResponse() {
        return new TrendsResponse(
                4, "2026-01-05", "2026-01-26",
                0.6, 0.5, 0.5, 1,
                0.75, 0.80, -0.05,
                Map.of("KING", 0.2, "QUEEN", 0.8),
                Map.of("DELIVERY", 1.0),
                List.of(),
                List.of()
        );
    }

    // ─── Valid requests ───────────────────────────────────────────────────────

    @Nested
    class ValidRequests {

        @Test
        void returnsOkWithDefaultWeeks() {
            TrendsResponse stubbed = stubResponse();
            when(trendsService.computeTrends(eq(ORG_ID), eq(USER_ID), eq(8)))
                    .thenReturn(stubbed);

            ResponseEntity<?> response = controller.getMyTrends(8);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stubbed, response.getBody());
        }

        @Test
        void delegatesToServiceWithCorrectParameters() {
            TrendsResponse stubbed = stubResponse();
            when(trendsService.computeTrends(eq(ORG_ID), eq(USER_ID), eq(4)))
                    .thenReturn(stubbed);

            controller.getMyTrends(4);

            verify(trendsService).computeTrends(ORG_ID, USER_ID, 4);
        }

        @Test
        void acceptsMinimumWeeksValue() {
            TrendsResponse stubbed = stubResponse();
            when(trendsService.computeTrends(eq(ORG_ID), eq(USER_ID), eq(1)))
                    .thenReturn(stubbed);

            ResponseEntity<?> response = controller.getMyTrends(1);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        void acceptsMaximumWeeksValue() {
            TrendsResponse stubbed = stubResponse();
            when(trendsService.computeTrends(eq(ORG_ID), eq(USER_ID), eq(26)))
                    .thenReturn(stubbed);

            ResponseEntity<?> response = controller.getMyTrends(26);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }
    }

    // ─── Invalid requests ─────────────────────────────────────────────────────

    @Nested
    class InvalidRequests {

        @Test
        void rejectsZeroWeeks() {
            ResponseEntity<?> response = controller.getMyTrends(0);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            assertNotNull(response.getBody());
            ApiErrorResponse error = (ApiErrorResponse) response.getBody();
            assertEquals("VALIDATION_ERROR", error.error().code());
        }

        @Test
        void rejectsNegativeWeeks() {
            ResponseEntity<?> response = controller.getMyTrends(-1);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        }

        @Test
        void rejectsWeeksAboveMax() {
            // 27 exceeds the MAX_WEEKS=26 limit
            ResponseEntity<?> response = controller.getMyTrends(27);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        }

        @Test
        void errorBodyContainsValidationErrorCode() {
            ResponseEntity<?> response = controller.getMyTrends(100);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            ApiErrorResponse error = (ApiErrorResponse) response.getBody();
            assertNotNull(error);
            assertEquals("VALIDATION_ERROR", error.error().code());
        }
    }
}
