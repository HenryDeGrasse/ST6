package com.weekly.rcdo;

import com.weekly.auth.AuthenticatedUserContext;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REST controller for RCDO hierarchy browsing and search.
 *
 * <p>GET /api/v1/rcdo/tree — full hierarchy for the org
 * <p>GET /api/v1/rcdo/search?q=... — typeahead search for outcomes
 *
 * <p>The caller's {@code orgId} is sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} exposed through
 * {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/rcdo")
public class RcdoController {

    private final RcdoClient rcdoClient;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final Environment environment;

    public RcdoController(
            RcdoClient rcdoClient,
            AuthenticatedUserContext authenticatedUserContext,
            Environment environment
    ) {
        this.rcdoClient = rcdoClient;
        this.authenticatedUserContext = authenticatedUserContext;
        this.environment = environment;
    }

    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> getTree() {
        RcdoTree tree = rcdoClient.getTree(authenticatedUserContext.orgId());
        return ResponseEntity.ok(Map.of("rallyCries", tree.rallyCries()));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String query
    ) {
        List<RcdoSearchResult> results = rcdoClient.search(authenticatedUserContext.orgId(), query);
        return ResponseEntity.ok(Map.of("results", results));
    }

    /**
     * Dev/test-only endpoint that refreshes (re-sets) the in-memory RCDO
     * cache for the caller's org, resetting the staleness clock.
     *
     * <p>Only available in local, dev, and test profiles. Returns 404 in
     * production-like environments.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (!activeProfiles.contains("local") && !activeProfiles.contains("dev")
                && !activeProfiles.contains("test")) {
            return ResponseEntity.notFound().build();
        }
        if (rcdoClient instanceof InMemoryRcdoClient inMemory) {
            var orgId = authenticatedUserContext.orgId();
            inMemory.unmarkStale(orgId);
            return ResponseEntity.ok(Map.of("status", "refreshed", "orgId", orgId.toString()));
        }
        return ResponseEntity.ok(Map.of("status", "no-op", "reason", "not using in-memory client"));
    }
}
