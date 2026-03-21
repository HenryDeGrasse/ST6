package com.weekly.quickupdate;

import com.weekly.auth.AuthenticatedUserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the rapid-fire batch check-in (Quick Update) flow.
 *
 * <p>POST /api/v1/plans/{planId}/quick-update — atomically records a progress
 * update for each commit listed in the request body.
 *
 * <p>The {@code orgId} and {@code userId} are sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} via {@link AuthenticatedUserContext}.
 * Exception handling for plan/commit domain errors is delegated to
 * {@link QuickUpdateExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1")
public class QuickUpdateController {

    private final QuickUpdateService quickUpdateService;
    private final AuthenticatedUserContext authCtx;

    public QuickUpdateController(
            QuickUpdateService quickUpdateService,
            AuthenticatedUserContext authCtx
    ) {
        this.quickUpdateService = quickUpdateService;
        this.authCtx = authCtx;
    }

    /**
     * Atomically applies a batch of commit check-ins to the given plan.
     *
     * @param planId  the plan to update
     * @param request the batch of per-commit updates
     * @return 200 OK with the number of entries created and their details
     */
    @PostMapping("/plans/{planId}/quick-update")
    public ResponseEntity<QuickUpdateResponseDto> quickUpdate(
            @PathVariable UUID planId,
            @Valid @RequestBody QuickUpdateRequestDto request
    ) {
        QuickUpdateResponseDto result = quickUpdateService.batchCheckIn(
                authCtx.orgId(), authCtx.userId(), planId, request.updates()
        );
        return ResponseEntity.ok(result);
    }
}
