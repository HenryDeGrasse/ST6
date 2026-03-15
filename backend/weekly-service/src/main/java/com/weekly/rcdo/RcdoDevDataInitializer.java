package com.weekly.rcdo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds the in-memory RCDO client with sample hierarchy data
 * for local development. Only active with the "local" profile.
 */
@Component
@Profile("local")
public class RcdoDevDataInitializer implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RcdoDevDataInitializer.class);

    /** Must match the seed-local.sh org ID. */
    private static final UUID DEV_ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    private final InMemoryRcdoClient rcdoClient;

    public RcdoDevDataInitializer(InMemoryRcdoClient rcdoClient) {
        this.rcdoClient = rcdoClient;
    }

    @Override
    public void run(String... args) {
        String rc1 = "10000000-0000-0000-0000-000000000001";
        String rc2 = "10000000-0000-0000-0000-000000000002";
        String rc3 = "10000000-0000-0000-0000-000000000003";

        String obj1 = "20000000-0000-0000-0000-000000000001";
        String obj2 = "20000000-0000-0000-0000-000000000002";
        String obj3 = "20000000-0000-0000-0000-000000000003";
        String obj4 = "20000000-0000-0000-0000-000000000004";
        String obj5 = "20000000-0000-0000-0000-000000000005";

        RcdoTree tree = new RcdoTree(List.of(
                new RcdoTree.RallyCry(rc1, "Scale to $500M ARR", List.of(
                        new RcdoTree.Objective(obj1, "Accelerate enterprise pipeline", rc1, List.of(
                                new RcdoTree.Outcome("e0000000-0000-0000-0000-000000000001", "Close 10 enterprise deals in Q1", obj1),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000002", "Launch enterprise demo environment", obj1),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000003", "Reduce sales cycle by 20%", obj1)
                        )),
                        new RcdoTree.Objective(obj2, "Expand into new verticals", rc1, List.of(
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000004", "Sign 3 healthcare pilot customers", obj2),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000005", "Complete SOC2 Type II certification", obj2)
                        ))
                )),
                new RcdoTree.RallyCry(rc2, "World-class engineering culture", List.of(
                        new RcdoTree.Objective(obj3, "Ship reliable software faster", rc2, List.of(
                                new RcdoTree.Outcome("e0000000-0000-0000-0000-000000000002", "Achieve 99.9% API uptime", obj3),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000007", "Reduce deploy-to-production time to < 15 min", obj3),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000008", "Increase unit test coverage to 85%", obj3)
                        )),
                        new RcdoTree.Objective(obj4, "Invest in team growth", rc2, List.of(
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000009", "Every engineer presents at a tech talk", obj4),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000010", "Implement weekly commitments module", obj4)
                        ))
                )),
                new RcdoTree.RallyCry(rc3, "Customer obsession", List.of(
                        new RcdoTree.Objective(obj5, "Reduce churn to < 5%", rc3, List.of(
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000011", "Launch proactive health-score alerting", obj5),
                                new RcdoTree.Outcome("30000000-0000-0000-0000-000000000012", "Achieve NPS > 60", obj5)
                        ))
                ))
        ));

        rcdoClient.setTree(DEV_ORG_ID, tree);
        LOG.info("RCDO dev data initialized: {} rally cries, {} total outcomes for org {}",
                tree.rallyCries().size(),
                tree.rallyCries().stream()
                        .flatMap(rc -> rc.objectives().stream())
                        .mapToLong(obj -> obj.outcomes().size())
                        .sum(),
                DEV_ORG_ID);
    }
}
