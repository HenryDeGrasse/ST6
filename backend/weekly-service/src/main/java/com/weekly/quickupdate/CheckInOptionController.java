package com.weekly.quickupdate;

import com.weekly.auth.AuthenticatedUserContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes the AI-powered check-in option generation
 * endpoint for the Quick Update card.
 *
 * <p>POST /api/v1/ai/check-in-options — returns a set of personalised status
 * and progress text suggestions for a given commitment.
 *
 * <p>Following the established AI-endpoint contract (see {@code AiController}),
 * this endpoint <em>always returns HTTP 200</em>. When the LLM is unavailable,
 * the commit cannot be found, or any parsing error occurs,
 * {@link CheckInOptionService} falls back to a safe empty response so that the
 * Quick Update card degrades gracefully (PRD §4 fallback contract).
 *
 * <p>The caller's identity ({@code orgId} and {@code userId}) is sourced from
 * the validated {@link com.weekly.auth.UserPrincipal} via
 * {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/ai")
public class CheckInOptionController {

    private final CheckInOptionService checkInOptionService;
    private final AuthenticatedUserContext authCtx;

    public CheckInOptionController(
            CheckInOptionService checkInOptionService,
            AuthenticatedUserContext authCtx
    ) {
        this.checkInOptionService = checkInOptionService;
        this.authCtx = authCtx;
    }

    /**
     * Generates personalised check-in options for the given commitment.
     *
     * <p>The response always has HTTP status 200. When no relevant data is
     * available (LLM unavailable, commit not found, parse error) the service
     * returns a safe fallback containing all standard {@code ProgressStatus}
     * values and an empty list of AI-generated progress options.
     *
     * @param request the check-in option request; {@code commitId} is required
     * @return 200 OK containing status options and AI-generated progress options
     */
    @PostMapping("/check-in-options")
    public ResponseEntity<CheckInOptionsResponse> getCheckInOptions(
            @Valid @RequestBody CheckInOptionRequestDto request
    ) {
        CheckInOptionsResponse result = checkInOptionService.generateOptions(
                authCtx.orgId(),
                authCtx.userId(),
                request.commitId(),
                request.currentStatus(),
                request.lastNote(),
                request.daysSinceLastCheckIn()
        );
        return ResponseEntity.ok(result);
    }
}
