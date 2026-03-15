package com.weekly.rcdo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ensures local dev RCDO seed data uses UUID identifiers so it is compatible
 * with the main domain model (weekly_commits.outcome_id + snapshot UUID fields).
 */
class RcdoDevDataInitializerTest {

    @Test
    void seedsOnlyUuidIdentifiers() throws Exception {
        InMemoryRcdoClient client = new InMemoryRcdoClient();
        RcdoDevDataInitializer initializer = new RcdoDevDataInitializer(client);

        initializer.run();

        UUID orgId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        RcdoTree tree = client.getTree(orgId);

        assertFalse(tree.rallyCries().isEmpty());

        tree.rallyCries().forEach(rc -> {
            assertDoesNotThrow(() -> UUID.fromString(rc.id()));
            rc.objectives().forEach(obj -> {
                assertDoesNotThrow(() -> UUID.fromString(obj.id()));
                assertDoesNotThrow(() -> UUID.fromString(obj.rallyCryId()));
                obj.outcomes().forEach(outcome -> {
                    assertDoesNotThrow(() -> UUID.fromString(outcome.id()));
                    assertDoesNotThrow(() -> UUID.fromString(outcome.objectiveId()));
                });
            });
        });
    }
}
