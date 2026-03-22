package com.weekly;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the Flyway-managed PostgreSQL schema is compatible with the JPA model.
 *
 * <p>This protects the production path that uses Flyway + ddl-auto=validate rather than
 * the H2 create-drop test profile used by controller tests.
 *
 * <p>Phase 6 additions: round-trip persistence tests for {@link TeamEntity},
 * {@link IssueEntity}, and {@link WeeklyAssignmentEntity} against the V16 schema.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class PostgresSchemaCompatibilityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("weekly")
            .withUsername("weekly")
            .withPassword("weekly");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private WeeklyPlanRepository planRepository;

    @Autowired
    private WeeklyCommitRepository commitRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private WeeklyAssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Existing tests ───────────────────────────────────────

    @Test
    void persistsCommitTagsAgainstMigratedPostgresSchema() {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now().with(DayOfWeek.MONDAY)
        );
        planRepository.saveAndFlush(plan);

        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(),
                plan.getOrgId(),
                plan.getId(),
                "Ship planning APIs"
        );
        commit.setTagsFromArray(new String[]{"api", "backend"});

        commitRepository.saveAndFlush(commit);
        entityManager.clear();

        WeeklyCommitEntity reloaded = commitRepository.findById(commit.getId()).orElseThrow();
        assertNotNull(reloaded.getCreatedAt());
        assertArrayEquals(new String[]{"api", "backend"}, reloaded.getTags());
    }

    // ─── Phase 6: TeamEntity round-trip ──────────────────────

    @Test
    void persistsTeamEntityAgainstV16Schema() {
        UUID orgId = UUID.randomUUID();
        TeamEntity team = new TeamEntity(
                UUID.randomUUID(),
                orgId,
                "Platform Engineering",
                "PLAT",
                UUID.randomUUID()
        );

        teamRepository.saveAndFlush(team);
        entityManager.clear();

        TeamEntity reloaded = teamRepository.findById(team.getId()).orElseThrow();
        assertNotNull(reloaded.getId());
        assertEquals("Platform Engineering", reloaded.getName());
        assertEquals("PLAT", reloaded.getKeyPrefix());
        assertEquals(0, reloaded.getIssueSequence());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
    }

    // ─── Phase 6: IssueEntity round-trip ─────────────────────

    @Test
    void persistsIssueEntityAgainstV16Schema() {
        UUID orgId = UUID.randomUUID();

        // A team must exist because issues.team_id has a FK to teams
        TeamEntity team = new TeamEntity(
                UUID.randomUUID(),
                orgId,
                "Issues Team " + UUID.randomUUID(),
                "ISS",
                UUID.randomUUID()
        );
        teamRepository.saveAndFlush(team);

        IssueEntity issue = new IssueEntity(
                UUID.randomUUID(),
                orgId,
                team.getId(),
                "ISS-1",
                1,
                "Implement OAuth flow",
                UUID.randomUUID()
        );
        issue.setEffortType(EffortType.BUILD);
        issue.setEstimatedHours(new BigDecimal("8.00"));

        issueRepository.saveAndFlush(issue);
        entityManager.clear();

        IssueEntity reloaded = issueRepository.findById(issue.getId()).orElseThrow();
        assertNotNull(reloaded.getId());
        assertEquals("ISS-1", reloaded.getIssueKey());
        assertEquals(1, reloaded.getSequenceNumber());
        assertEquals("Implement OAuth flow", reloaded.getTitle());
        assertEquals(EffortType.BUILD, reloaded.getEffortType());
        assertEquals(IssueStatus.OPEN, reloaded.getStatus());
        assertEquals(0, new BigDecimal("8.00").compareTo(reloaded.getEstimatedHours()));
        assertEquals(0, reloaded.getEmbeddingVersion());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
    }

    // ─── Phase 6: WeeklyAssignmentEntity round-trip ───────────

    @Test
    void persistsWeeklyAssignmentEntityAgainstV16Schema() {
        UUID orgId = UUID.randomUUID();

        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                UUID.randomUUID(),
                orgId,
                UUID.randomUUID(),
                LocalDate.now().with(DayOfWeek.MONDAY)
        );
        planRepository.saveAndFlush(plan);

        TeamEntity team = new TeamEntity(
                UUID.randomUUID(),
                orgId,
                "Assignment Team " + UUID.randomUUID(),
                "ASG",
                UUID.randomUUID()
        );
        teamRepository.saveAndFlush(team);

        IssueEntity issue = new IssueEntity(
                UUID.randomUUID(),
                orgId,
                team.getId(),
                "ASG-1",
                1,
                "Ship the backlog API",
                UUID.randomUUID()
        );
        issueRepository.saveAndFlush(issue);

        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(),
                orgId,
                plan.getId(),
                issue.getId()
        );
        assignment.setExpectedResult("Backlog CRUD live in staging");
        assignment.setConfidence(new BigDecimal("0.85"));
        assignment.setTagsFromArray(new String[]{"api", "phase6"});

        assignmentRepository.saveAndFlush(assignment);
        entityManager.clear();

        WeeklyAssignmentEntity reloaded = assignmentRepository.findById(assignment.getId()).orElseThrow();
        assertNotNull(reloaded.getId());
        assertEquals(plan.getId(), reloaded.getWeeklyPlanId());
        assertEquals(issue.getId(), reloaded.getIssueId());
        assertEquals("Backlog CRUD live in staging", reloaded.getExpectedResult());
        assertEquals(0, new BigDecimal("0.85").compareTo(reloaded.getConfidence()));
        assertArrayEquals(new String[]{"api", "phase6"}, reloaded.getTags());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
    }

    // ─── Phase 6: V17 migration invariant checks ─────────────────────────────

    /**
     * Seeds two commits — a chain-root and one carry-forward child — then
     * re-runs the V17 migration logic and verifies the three invariants:
     * <ol>
     *   <li>Issues count == distinct chain roots + standalone commits.</li>
     *   <li>All weekly_assignments have valid FK to issues.</li>
     *   <li>team.issue_sequence == MAX(issues.sequence_number) for the team.</li>
     * </ol>
     *
     * <p>Because Flyway has already executed V17 (which was a no-op on this
     * empty container), we insert fresh commit data that bypasses V17 and then
     * re-run the migration SQL segments manually to verify correctness.
     */
    @Test
    void v17MigrationCollapseCarryForwardChainIntoSingleIssue() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // ── Setup: plan + two commits (root → carried-forward child) ─────────
        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                UUID.randomUUID(), orgId, userId, LocalDate.now().with(DayOfWeek.MONDAY)
        );
        planRepository.saveAndFlush(plan);

        WeeklyPlanEntity prevPlan = new WeeklyPlanEntity(
                UUID.randomUUID(), orgId, userId,
                LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1)
        );
        planRepository.saveAndFlush(prevPlan);

        UUID rootCommitId = UUID.randomUUID();
        WeeklyCommitEntity rootCommit = new WeeklyCommitEntity(
                rootCommitId, orgId, prevPlan.getId(), "Root work item"
        );
        commitRepository.saveAndFlush(rootCommit);

        UUID childCommitId = UUID.randomUUID();
        // Manually insert the carry-forward child because setCarriedFromCommitId
        // doesn't bump version the same way
        jdbc.update(
                "INSERT INTO weekly_commits "
                + "(id, org_id, weekly_plan_id, title, description, expected_result, "
                + " progress_notes, tags, carried_from_commit_id, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, '', '', '', '{}', ?, 1, NOW(), NOW())",
                childCommitId, orgId, plan.getId(),
                "Carried-forward work item", rootCommitId
        );
        jdbc.update(
                "INSERT INTO weekly_commit_actuals "
                + "(commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'DONE', 2.5, NOW(), NOW())",
                childCommitId, orgId, "Completed after carry-forward"
        );

        // ── Run V17 chain-collapse and issue-creation logic ──────────────────
        // Create a General team for our test org
        UUID teamId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO teams (id, org_id, name, key_prefix, description, owner_user_id, "
                + "issue_sequence, version, created_at, updated_at) "
                + "VALUES (?, ?, 'General', 'GEN', '', ?, 0, 1, NOW(), NOW()) "
                + "ON CONFLICT (org_id, name) DO NOTHING",
                teamId, orgId, userId
        );

        // Build chain table
        jdbc.execute(
                "CREATE TEMP TABLE IF NOT EXISTS _v17_test_chains AS "
                + "WITH RECURSIVE c AS ("
                + "  SELECT id AS commit_id, id AS chain_root_id, 0 AS depth "
                + "  FROM weekly_commits WHERE org_id = '" + orgId + "' "
                + "  AND carried_from_commit_id IS NULL "
                + "  UNION ALL "
                + "  SELECT wc.id, c.chain_root_id, c.depth + 1 "
                + "  FROM weekly_commits wc JOIN c ON wc.carried_from_commit_id = c.commit_id"
                + ") SELECT * FROM c"
        );

        // Build issue-mapping table
        jdbc.execute(
                "CREATE TEMP TABLE IF NOT EXISTS _v17_test_issues AS "
                + "WITH mr AS ("
                + "  SELECT DISTINCT ON (cc.chain_root_id) "
                + "    cc.chain_root_id, wc.org_id, wc.title, "
                + "    t.id AS team_id, t.key_prefix, "
                + "    ROW_NUMBER() OVER (PARTITION BY wc.org_id ORDER BY cc.chain_root_id)::integer AS seq "
                + "  FROM _v17_test_chains cc "
                + "  JOIN weekly_commits wc ON wc.id = cc.commit_id "
                + "  JOIN teams t ON t.org_id = wc.org_id AND t.name = 'General' "
                + "  ORDER BY cc.chain_root_id, cc.depth DESC "
                + ") "
                + "SELECT gen_random_uuid() AS issue_id, chain_root_id, org_id, team_id, "
                + "  key_prefix || '-' || seq AS issue_key, seq AS sequence_number, title "
                + "FROM mr"
        );

        // Insert issues
        jdbc.execute(
                "INSERT INTO issues "
                + "(id, org_id, team_id, issue_key, sequence_number, title, description, "
                + " creator_user_id, status, embedding_version, version, created_at, updated_at) "
                + "SELECT issue_id, org_id, team_id, issue_key, sequence_number, title, '', "
                + "  '" + userId + "', 'OPEN', 0, 1, NOW(), NOW() "
                + "FROM _v17_test_issues "
                + "ON CONFLICT (org_id, issue_key) DO NOTHING"
        );

        // Insert assignments
        jdbc.execute(
                "INSERT INTO weekly_assignments "
                + "(id, org_id, weekly_plan_id, issue_id, expected_result, tags, legacy_commit_id, "
                + " version, created_at, updated_at) "
                + "SELECT gen_random_uuid(), wc.org_id, wc.weekly_plan_id, im.issue_id, '', '{}', wc.id, "
                + "  1, NOW(), NOW() "
                + "FROM weekly_commits wc "
                + "JOIN _v17_test_chains cc ON cc.commit_id = wc.id "
                + "JOIN _v17_test_issues im ON im.chain_root_id = cc.chain_root_id "
                + "WHERE wc.org_id = '" + orgId + "' "
                + "ON CONFLICT (weekly_plan_id, issue_id) DO NOTHING"
        );

        // Back-populate source_issue_id crosswalk
        jdbc.execute(
                "UPDATE weekly_commits wc SET source_issue_id = im.issue_id "
                + "FROM _v17_test_chains cc "
                + "JOIN _v17_test_issues im ON im.chain_root_id = cc.chain_root_id "
                + "WHERE wc.id = cc.commit_id AND wc.source_issue_id IS NULL"
        );

        // Migrate actuals
        jdbc.execute(
                "INSERT INTO weekly_assignment_actuals "
                + "(assignment_id, org_id, actual_result, completion_status, delta_reason, hours_spent, created_at, updated_at) "
                + "SELECT wa.id, wa.org_id, COALESCE(wca.actual_result, ''), wca.completion_status, wca.delta_reason, "
                + "       COALESCE(wca.actual_hours, CASE WHEN wca.time_spent IS NOT NULL THEN (wca.time_spent::numeric(10,4) / 60.0) ELSE NULL END), "
                + "       wca.created_at, wca.updated_at "
                + "FROM weekly_assignments wa "
                + "JOIN weekly_commit_actuals wca ON wca.commit_id = wa.legacy_commit_id "
                + "WHERE wa.legacy_commit_id IS NOT NULL "
                + "ON CONFLICT (assignment_id) DO NOTHING"
        );

        // Update statuses from most recent actual / active week assignment
        jdbc.execute(
                "UPDATE issues i SET status = 'DONE' "
                + "FROM _v17_test_issues im "
                + "WHERE im.issue_id = i.id AND i.status = 'OPEN' AND EXISTS ("
                + "  SELECT 1 FROM _v17_test_chains cc2 "
                + "  JOIN weekly_commits wc2 ON wc2.id = cc2.commit_id "
                + "  JOIN weekly_commit_actuals wca ON wca.commit_id = wc2.id "
                + "  WHERE cc2.chain_root_id = im.chain_root_id "
                + "    AND cc2.depth = ("
                + "      SELECT MAX(cc3.depth) FROM _v17_test_chains cc3 "
                + "      JOIN weekly_commit_actuals wca3 ON wca3.commit_id = cc3.commit_id "
                + "      WHERE cc3.chain_root_id = im.chain_root_id"
                + "    ) "
                + "    AND wca.completion_status = 'DONE'"
                + ")"
        );
        jdbc.execute(
                "UPDATE issues i SET status = 'IN_PROGRESS' "
                + "WHERE i.status = 'OPEN' AND EXISTS ("
                + "  SELECT 1 FROM weekly_assignments wa "
                + "  JOIN weekly_plans wp ON wp.id = wa.weekly_plan_id "
                + "  WHERE wa.issue_id = i.id "
                + "    AND wp.week_start_date = date_trunc('week', CURRENT_DATE)::date "
                + "    AND wp.state NOT IN ('RECONCILED', 'CARRY_FORWARD')"
                + ")"
        );

        // Update team issue_sequence
        jdbc.execute(
                "UPDATE teams t SET issue_sequence = sub.max_seq "
                + "FROM (SELECT team_id, MAX(sequence_number) AS max_seq FROM issues GROUP BY team_id) sub "
                + "WHERE sub.team_id = t.id AND sub.max_seq > t.issue_sequence"
        );

        // Cleanup temp tables
        jdbc.execute("DROP TABLE IF EXISTS _v17_test_chains");
        jdbc.execute("DROP TABLE IF EXISTS _v17_test_issues");

        // ── Verify invariant A: 1 issue for the 2-commit chain (not 2) ───────
        // count via JDBC to avoid RLS
        int issueCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM issues WHERE org_id = ?", Integer.class, orgId
        );
        // Two commits in one chain → exactly 1 issue
        assertEquals(1, issueCount,
                "Two commits in a carry-forward chain should collapse to exactly one issue");

        // ── Verify invariant B: both assignments have valid FKs ───────────────
        int assignmentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_assignments wa "
                + "JOIN issues i ON i.id = wa.issue_id "
                + "WHERE wa.org_id = ?", Integer.class, orgId
        );
        // Both commits (root + child) create one assignment each, so 2 assignments
        assertEquals(2, assignmentCount,
                "Should have 2 weekly_assignments (one per commit), all with valid issue FK");

        // ── Verify invariant C: bidirectional crosswalk is intact ───────────
        int crosswalkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_assignments wa "
                + "JOIN weekly_commits wc ON wc.id = wa.legacy_commit_id "
                + "WHERE wa.org_id = ? AND wc.source_issue_id = wa.issue_id",
                Integer.class,
                orgId
        );
        assertEquals(2, crosswalkCount,
                "Every migrated assignment should point back to a commit whose source_issue_id matches the same issue");

        int assignmentActualCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_assignment_actuals waa "
                + "JOIN weekly_assignments wa ON wa.id = waa.assignment_id "
                + "WHERE wa.org_id = ?",
                Integer.class,
                orgId
        );
        assertEquals(1, assignmentActualCount,
                "Commit actuals should be migrated to weekly_assignment_actuals");

        String issueStatus = jdbc.queryForObject(
                "SELECT status FROM issues WHERE org_id = ?",
                String.class,
                orgId
        );
        assertEquals("DONE", issueStatus,
                "Most recent DONE actual in the chain should mark the collapsed issue DONE");

        // ── Verify invariant D: team issue_sequence == max(sequence_number) ──
        int maxSeq = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_number), 0) FROM issues "
                + "WHERE org_id = ?", Integer.class, orgId
        );
        int teamSeq = jdbc.queryForObject(
                "SELECT issue_sequence FROM teams WHERE org_id = ? AND name = 'General'",
                Integer.class, orgId
        );
        assertEquals(maxSeq, teamSeq,
                "team.issue_sequence should equal max(issues.sequence_number)");
        assertTrue(teamSeq > 0, "issue_sequence should be positive after migration");
    }

    /**
     * Verifies that standalone commits (no carry-forward chain) each produce
     * exactly one issue and one assignment, with the bidirectional crosswalk intact.
     */
    @Test
    void v17MigrationStandaloneCommitProducesOneIssueAndOneAssignment() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                UUID.randomUUID(), orgId, userId, LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(2)
        );
        planRepository.saveAndFlush(plan);

        // Three standalone commits (no carried_from_commit_id)
        for (int i = 0; i < 3; i++) {
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), orgId, plan.getId(), "Standalone issue " + i
            );
            commitRepository.saveAndFlush(commit);
        }

        UUID teamId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO teams (id, org_id, name, key_prefix, description, owner_user_id, "
                + "issue_sequence, version, created_at, updated_at) "
                + "VALUES (?, ?, 'General', 'GEN', '', ?, 0, 1, NOW(), NOW()) "
                + "ON CONFLICT (org_id, name) DO NOTHING",
                teamId, orgId, userId
        );

        // Run migration steps
        jdbc.execute(
                "CREATE TEMP TABLE _v17_sa_chains AS "
                + "WITH RECURSIVE c AS ("
                + "  SELECT id AS commit_id, id AS chain_root_id, 0 AS depth "
                + "  FROM weekly_commits WHERE org_id = '" + orgId + "' "
                + "  AND carried_from_commit_id IS NULL "
                + "  UNION ALL "
                + "  SELECT wc.id, c.chain_root_id, c.depth + 1 "
                + "  FROM weekly_commits wc JOIN c ON wc.carried_from_commit_id = c.commit_id"
                + ") SELECT * FROM c"
        );

        jdbc.execute(
                "CREATE TEMP TABLE _v17_sa_issues AS "
                + "WITH mr AS ("
                + "  SELECT DISTINCT ON (cc.chain_root_id) "
                + "    cc.chain_root_id, wc.org_id, wc.title, "
                + "    t.id AS team_id, t.key_prefix, "
                + "    ROW_NUMBER() OVER (PARTITION BY wc.org_id ORDER BY cc.chain_root_id)::integer AS seq "
                + "  FROM _v17_sa_chains cc "
                + "  JOIN weekly_commits wc ON wc.id = cc.commit_id "
                + "  JOIN teams t ON t.org_id = wc.org_id AND t.name = 'General' "
                + "  ORDER BY cc.chain_root_id, cc.depth DESC "
                + ") "
                + "SELECT gen_random_uuid() AS issue_id, chain_root_id, org_id, team_id, "
                + "  key_prefix || '-' || seq AS issue_key, seq AS sequence_number, title "
                + "FROM mr"
        );

        jdbc.execute(
                "INSERT INTO issues "
                + "(id, org_id, team_id, issue_key, sequence_number, title, description, "
                + " creator_user_id, status, embedding_version, version, created_at, updated_at) "
                + "SELECT issue_id, org_id, team_id, issue_key, sequence_number, title, '', "
                + "  '" + userId + "', 'OPEN', 0, 1, NOW(), NOW() "
                + "FROM _v17_sa_issues ON CONFLICT (org_id, issue_key) DO NOTHING"
        );

        jdbc.execute(
                "INSERT INTO weekly_assignments "
                + "(id, org_id, weekly_plan_id, issue_id, expected_result, tags, legacy_commit_id, "
                + " version, created_at, updated_at) "
                + "SELECT gen_random_uuid(), wc.org_id, wc.weekly_plan_id, im.issue_id, '', '{}', wc.id, "
                + "  1, NOW(), NOW() "
                + "FROM weekly_commits wc "
                + "JOIN _v17_sa_chains cc ON cc.commit_id = wc.id "
                + "JOIN _v17_sa_issues im ON im.chain_root_id = cc.chain_root_id "
                + "WHERE wc.org_id = '" + orgId + "' "
                + "ON CONFLICT (weekly_plan_id, issue_id) DO NOTHING"
        );

        // Back-populate source_issue_id
        jdbc.execute(
                "UPDATE weekly_commits wc SET source_issue_id = im.issue_id "
                + "FROM _v17_sa_chains cc "
                + "JOIN _v17_sa_issues im ON im.chain_root_id = cc.chain_root_id "
                + "WHERE wc.id = cc.commit_id AND wc.source_issue_id IS NULL"
        );

        jdbc.execute("DROP TABLE IF EXISTS _v17_sa_chains");
        jdbc.execute("DROP TABLE IF EXISTS _v17_sa_issues");

        // Verify: 3 standalone commits → 3 issues, 3 assignments
        int issueCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM issues WHERE org_id = ?", Integer.class, orgId
        );
        int assignmentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_assignments wa "
                + "JOIN issues i ON i.id = wa.issue_id WHERE wa.org_id = ?",
                Integer.class, orgId
        );
        int crosswalkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_commits WHERE org_id = ? AND source_issue_id IS NOT NULL",
                Integer.class, orgId
        );

        int bidirectionalCrosswalkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_assignments wa "
                + "JOIN weekly_commits wc ON wc.id = wa.legacy_commit_id "
                + "WHERE wa.org_id = ? AND wc.source_issue_id = wa.issue_id",
                Integer.class,
                orgId
        );

        assertEquals(3, issueCount, "3 standalone commits → 3 issues");
        assertEquals(3, assignmentCount, "3 standalone commits → 3 assignments");
        assertEquals(3, crosswalkCount,
                "All commits should have source_issue_id back-populated");
        assertEquals(3, bidirectionalCrosswalkCount,
                "Every standalone migrated assignment should agree with weekly_commits.source_issue_id");
    }
}
