package com.weekly.plan.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.plan.dto.CreateCommitRequest;
import com.weekly.plan.dto.UpdateActualRequest;
import com.weekly.plan.dto.UpdateCommitRequest;
import com.weekly.plan.dto.WeeklyCommitActualResponse;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.service.ActualService;
import com.weekly.plan.service.CommitService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for weekly commit CRUD operations and actuals.
 *
 * <p>The {@code orgId} and {@code userId} are sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} exposed through
 * {@link AuthenticatedUserContext} — never from raw headers (§9.1).
 */
@RestController
@RequestMapping("/api/v1")
public class CommitController {

    private final CommitService commitService;
    private final ActualService actualService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public CommitController(
            CommitService commitService,
            ActualService actualService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.commitService = commitService;
        this.actualService = actualService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @GetMapping("/plans/{planId}/commits")
    public ResponseEntity<List<WeeklyCommitResponse>> listCommits(
            @PathVariable UUID planId
    ) {
        List<WeeklyCommitResponse> commits = commitService.listCommits(
                authenticatedUserContext.orgId(),
                planId
        );
        return ResponseEntity.ok(commits);
    }

    @PostMapping("/plans/{planId}/commits")
    public ResponseEntity<WeeklyCommitResponse> createCommit(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateCommitRequest request
    ) {
        WeeklyCommitResponse commit = commitService.createCommit(
                authenticatedUserContext.orgId(),
                planId,
                request,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(commit);
    }

    @PatchMapping("/commits/{commitId}")
    public ResponseEntity<WeeklyCommitResponse> updateCommit(
            @PathVariable UUID commitId,
            @RequestHeader("If-Match") int ifMatch,
            @Valid @RequestBody UpdateCommitRequest request
    ) {
        WeeklyCommitResponse commit = commitService.updateCommit(
                authenticatedUserContext.orgId(),
                commitId,
                ifMatch,
                request,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(commit);
    }

    @DeleteMapping("/commits/{commitId}")
    public ResponseEntity<Void> deleteCommit(
            @PathVariable UUID commitId
    ) {
        commitService.deleteCommit(
                authenticatedUserContext.orgId(),
                commitId,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/commits/{commitId}/actual")
    public ResponseEntity<WeeklyCommitActualResponse> updateActual(
            @PathVariable UUID commitId,
            @RequestHeader("If-Match") int ifMatch,
            @Valid @RequestBody UpdateActualRequest request
    ) {
        WeeklyCommitActualResponse actual = actualService.updateActual(
                authenticatedUserContext.orgId(),
                commitId,
                ifMatch,
                request,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(actual);
    }
}
