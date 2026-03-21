package com.weekly;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

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
}
