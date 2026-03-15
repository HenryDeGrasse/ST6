package com.weekly.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds the in-memory org graph client with sample user data
 * for local development. Only active with the "local" profile.
 *
 * <p>Registers display names for the dev user IDs used by
 * {@code seed-local.sh} and the frontend dev stub.
 */
@Component
@Profile("local")
public class OrgGraphDevDataInitializer implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(OrgGraphDevDataInitializer.class);

    /** Must match the seed-local.sh org ID. */
    private static final UUID DEV_ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    /** IC / Manager dev user from seed-local.sh and .env. */
    private static final UUID DEV_USER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    private final InMemoryOrgGraphClient orgGraphClient;

    public OrgGraphDevDataInitializer(InMemoryOrgGraphClient orgGraphClient) {
        this.orgGraphClient = orgGraphClient;
    }

    @Override
    public void run(String... args) {
        // Register display names for dev users
        orgGraphClient.registerUser(DEV_USER_ID, "Dev User");

        // The dev user is both IC and manager. As manager, they manage themselves
        // for demo purposes (this lets the same user see both IC and manager views).
        orgGraphClient.setDirectReports(DEV_ORG_ID, DEV_USER_ID, List.of(DEV_USER_ID));

        LOG.info("Org graph dev data initialized: registered user {} with display name for org {}",
                DEV_USER_ID, DEV_ORG_ID);
    }
}
