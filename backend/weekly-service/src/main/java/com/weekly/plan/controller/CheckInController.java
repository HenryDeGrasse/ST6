package com.weekly.plan.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.plan.dto.CheckInEntryResponse;
import com.weekly.plan.dto.CheckInHistoryResponse;
import com.weekly.plan.dto.CheckInRequest;
import com.weekly.plan.service.CheckInService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Quick Daily Check-In operations (Wave 2).
 *
 * <p>POST /api/v1/commits/{commitId}/check-in — appends a structured progress
 * micro-update to a weekly commit.
 *
 * <p>GET /api/v1/commits/{commitId}/check-ins — returns the append-only history.
 *
 * <p>The {@code orgId} and {@code userId} are sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} via {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1")
public class CheckInController {

    private final CheckInService checkInService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public CheckInController(
            CheckInService checkInService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.checkInService = checkInService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * Appends a daily check-in entry to the given commit.
     *
     * @param commitId the commit to attach the check-in to
     * @param request  the check-in status and optional note
     * @return 201 Created with the new entry
     */
    @PostMapping("/commits/{commitId}/check-in")
    public ResponseEntity<CheckInEntryResponse> addCheckIn(
            @PathVariable UUID commitId,
            @Valid @RequestBody CheckInRequest request
    ) {
        CheckInEntryResponse entry = checkInService.addCheckIn(
                authenticatedUserContext.orgId(),
                commitId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    /**
     * Returns the complete check-in history for a commit, ordered oldest-first.
     *
     * @param commitId the commit whose history to retrieve
     * @return 200 OK with the append-only history
     */
    @GetMapping("/commits/{commitId}/check-ins")
    public ResponseEntity<CheckInHistoryResponse> getCheckIns(
            @PathVariable UUID commitId
    ) {
        CheckInHistoryResponse history = checkInService.getHistory(
                authenticatedUserContext.orgId(),
                commitId
        );
        return ResponseEntity.ok(history);
    }
}
