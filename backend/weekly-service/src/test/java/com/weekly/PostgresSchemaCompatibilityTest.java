package com.weekly;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
