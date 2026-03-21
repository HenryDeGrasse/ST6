package com.weekly.usermodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link UserProfileController}.
 */
class UserProfileControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private UserModelService userModelService;
    private AuthenticatedUserContext authenticatedUserContext;
    private UserProfileController controller;

    @BeforeEach
    void setUp() {
        userModelService = mock(UserModelService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );
        controller = new UserProfileController(authenticatedUserContext, userModelService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsExistingSnapshotForAuthenticatedUser() {
        UserProfileResponse profile = new UserProfileResponse(
                USER_ID.toString(),
                6,
                new UserProfileResponse.PerformanceProfile(0.7, 0.8, 4.0, 0.5, List.of(), java.util.Map.of(), java.util.Map.of()),
                new UserProfileResponse.Preferences("1K-2Q", List.of("Weekly ops review"), 2.0, List.of("MONDAY")),
                new UserProfileResponse.Trends("IMPROVING", "STABLE", "WORSENING")
        );
        when(userModelService.getSnapshot(ORG_ID, USER_ID)).thenReturn(Optional.of(profile));

        ResponseEntity<UserProfileResponse> response = controller.getMyProfile();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(profile, response.getBody());
        verify(userModelService).getSnapshot(ORG_ID, USER_ID);
    }

    @Test
    void returnsEmptyProfileWhenNoSnapshotExists() {
        when(userModelService.getSnapshot(ORG_ID, USER_ID)).thenReturn(Optional.empty());

        ResponseEntity<UserProfileResponse> response = controller.getMyProfile();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(USER_ID.toString(), response.getBody().userId());
        assertEquals(0, response.getBody().weeksAnalyzed());
        assertNull(response.getBody().performanceProfile());
        assertNull(response.getBody().preferences());
        assertNull(response.getBody().trends());
        verify(userModelService).getSnapshot(ORG_ID, USER_ID);
    }
}
