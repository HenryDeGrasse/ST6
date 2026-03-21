package com.weekly.auth;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the in-memory org graph client with sample user data
 * for local development. Only active with the "local" profile.
 *
 * <p>Three personas for demo:
 * <ul>
 *   <li>Alice Chen (IC) — c0…0010</li>
 *   <li>Bob Martinez (IC) — c0…0020</li>
 *   <li>Carol Park (Manager + IC) — c0…0001 — manages Alice & Bob</li>
 * </ul>
 */
@Component
@Profile("local")
public class OrgGraphDevDataInitializer implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(OrgGraphDevDataInitializer.class);

    private static final UUID DEV_ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    private static final UUID CAROL_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID ALICE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000010");
    private static final UUID BOB_ID   = UUID.fromString("c0000000-0000-0000-0000-000000000020");

    private final InMemoryOrgGraphClient orgGraphClient;

    public OrgGraphDevDataInitializer(InMemoryOrgGraphClient orgGraphClient) {
        this.orgGraphClient = orgGraphClient;
    }

    @Override
    public void run(String... args) {
        // Register display names
        orgGraphClient.registerUser(CAROL_ID, "Carol Park");
        orgGraphClient.registerUser(ALICE_ID, "Alice Chen");
        orgGraphClient.registerUser(BOB_ID,   "Bob Martinez");

        // Carol manages Alice and Bob
        orgGraphClient.setDirectReports(DEV_ORG_ID, CAROL_ID, List.of(ALICE_ID, BOB_ID));

        LOG.info("Org graph dev data initialized: Carol (manager) → Alice, Bob for org {}", DEV_ORG_ID);
    }
}
