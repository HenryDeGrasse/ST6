package com.weekly.plan.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.plan.dto.CheckInEntryResponse;
import com.weekly.plan.dto.CheckInHistoryResponse;
import com.weekly.plan.dto.CheckInRequest;
import com.weekly.plan.service.CheckInService;
import java.time.Instant;
import java.util.List;
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
 * Unit tests for {@link CheckInController}: POST check-in and GET history endpoints.
 */
class CheckInControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private CheckInService checkInService;
    private AuthenticatedUserContext authenticatedUserContext;
    private CheckInController controller;

    @BeforeEach
    void setUp() {
        checkInService = mock(CheckInService.class);
        authenticatedUserContext = new AuthenticatedUserContext();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        controller = new CheckInController(checkInService, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private CheckInEntryResponse stubEntry(UUID commitId, String status) {
        return new CheckInEntryResponse(
                UUID.randomUUID().toString(),
                commitId.toString(),
                status,
                "Test note",
                Instant.now().toString()
        );
    }

    // ─── POST /commits/{commitId}/check-in ────────────────────────────────────

    @Nested
    class AddCheckIn {

        @Test
        void returns201WithNewEntry() {
            UUID commitId = UUID.randomUUID();
            CheckInRequest request = new CheckInRequest("ON_TRACK", "Progressing well");
            CheckInEntryResponse stubResponse = stubEntry(commitId, "ON_TRACK");

            when(checkInService.addCheckIn(eq(ORG_ID), eq(commitId), any()))
                    .thenReturn(stubResponse);

            ResponseEntity<CheckInEntryResponse> response =
                    controller.addCheckIn(commitId, request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(commitId.toString(), response.getBody().commitId());
            assertEquals("ON_TRACK", response.getBody().status());
        }

        @Test
        void delegatesToServiceWithCorrectArguments() {
            UUID commitId = UUID.randomUUID();
            CheckInRequest request = new CheckInRequest("BLOCKED", "Stuck on review");

            when(checkInService.addCheckIn(any(), any(), any()))
                    .thenReturn(stubEntry(commitId, "BLOCKED"));

            controller.addCheckIn(commitId, request);

            verify(checkInService).addCheckIn(ORG_ID, commitId, request);
        }

        @Test
        void usesOrgIdFromAuthContext() {
            UUID commitId = UUID.randomUUID();
            CheckInRequest request = new CheckInRequest("AT_RISK", "Risk identified");

            when(checkInService.addCheckIn(any(), any(), any()))
                    .thenReturn(stubEntry(commitId, "AT_RISK"));

            controller.addCheckIn(commitId, request);

            // Verify it used the org from the security context, not a raw header
            verify(checkInService).addCheckIn(eq(ORG_ID), eq(commitId), any());
        }
    }

    // ─── GET /commits/{commitId}/check-ins ────────────────────────────────────

    @Nested
    class GetCheckIns {

        @Test
        void returns200WithHistory() {
            UUID commitId = UUID.randomUUID();
            CheckInEntryResponse entry1 = stubEntry(commitId, "ON_TRACK");
            CheckInEntryResponse entry2 = stubEntry(commitId, "AT_RISK");
            CheckInHistoryResponse stubResponse = new CheckInHistoryResponse(
                    commitId.toString(), List.of(entry1, entry2)
            );

            when(checkInService.getHistory(ORG_ID, commitId)).thenReturn(stubResponse);

            ResponseEntity<CheckInHistoryResponse> response =
                    controller.getCheckIns(commitId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(commitId.toString(), response.getBody().commitId());
            assertEquals(2, response.getBody().entries().size());
        }

        @Test
        void returnsEmptyHistoryWhenNoEntries() {
            UUID commitId = UUID.randomUUID();
            CheckInHistoryResponse stubResponse = new CheckInHistoryResponse(
                    commitId.toString(), List.of()
            );

            when(checkInService.getHistory(ORG_ID, commitId)).thenReturn(stubResponse);

            ResponseEntity<CheckInHistoryResponse> response =
                    controller.getCheckIns(commitId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(0, response.getBody().entries().size());
        }

        @Test
        void delegatesToServiceWithCorrectArguments() {
            UUID commitId = UUID.randomUUID();
            when(checkInService.getHistory(any(), any()))
                    .thenReturn(new CheckInHistoryResponse(commitId.toString(), List.of()));

            controller.getCheckIns(commitId);

            verify(checkInService).getHistory(ORG_ID, commitId);
        }

        @Test
        void usesOrgIdFromAuthContext() {
            UUID commitId = UUID.randomUUID();
            when(checkInService.getHistory(any(), any()))
                    .thenReturn(new CheckInHistoryResponse(commitId.toString(), List.of()));

            controller.getCheckIns(commitId);

            verify(checkInService).getHistory(eq(ORG_ID), eq(commitId));
        }
    }
}
