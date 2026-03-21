package com.weekly.usermodel;

import com.weekly.auth.AuthenticatedUserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user model profile data.
 *
 * <p>GET /api/v1/users/me/profile
 *
 * <p>Returns the most recently computed user model snapshot for the authenticated
 * user. If no snapshot has been computed yet, returns a 200 response with
 * {@code weeksAnalyzed=0} and {@code null} nested objects.
 *
 * <p>The caller's identity is sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} via {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final AuthenticatedUserContext authCtx;
    private final UserModelService userModelService;

    public UserProfileController(
            AuthenticatedUserContext authCtx,
            UserModelService userModelService) {
        this.authCtx = authCtx;
        this.userModelService = userModelService;
    }

    /**
     * Returns the user model profile for the authenticated user.
     *
     * <p>If no snapshot exists yet (e.g. the compute job has not run),
     * a 200 response is still returned with {@code weeksAnalyzed=0} and
     * {@code null} nested profile objects.
     *
     * @return 200 with {@link UserProfileResponse}
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        UserProfileResponse profile = userModelService
                .getSnapshot(authCtx.orgId(), authCtx.userId())
                .orElseGet(() -> emptyProfile(authCtx.userId().toString()));
        return ResponseEntity.ok(profile);
    }

    private static UserProfileResponse emptyProfile(String userId) {
        return new UserProfileResponse(userId, 0, null, null, null);
    }
}
