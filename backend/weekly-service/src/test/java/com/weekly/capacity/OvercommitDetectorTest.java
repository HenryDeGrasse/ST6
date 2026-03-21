package com.weekly.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.shared.OvercommitLevel;
import com.weekly.shared.OvercommitWarning;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OvercommitDetector}.
 */
class OvercommitDetectorTest {

    private OvercommitDetector overcommitDetector;

    @BeforeEach
    void setUp() {
        overcommitDetector = new OvercommitDetector(new ObjectMapper());
    }

    @Nested
    class DetectOvercommitment {

        // ── NONE level ────────────────────────────────────────────────────────

        @Test
        void returnsNoneWhenAdjustedTotalIsLessThanRealisticCap() {
            // 10h * 1.0 globalBias = 10.0h < cap 20.0h → NONE
            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.0"),
                    new BigDecimal("20.0"),
                    "[]",
                    4);

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.DELIVERY, "10.0")), profile);

            assertEquals(OvercommitLevel.NONE, warning.level());
            assertEquals("", warning.message());
            assertEquals(new BigDecimal("10.0"), warning.adjustedTotal());
        }

        @Test
        void returnsNoneWhenNullProfile() {
            // Null profile → cannot detect; must return NONE with zero totals
            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.DELIVERY, "30.0")),
                    null);

            assertEquals(OvercommitLevel.NONE, warning.level());
            assertEquals("", warning.message());
            assertEquals(BigDecimal.ZERO, warning.adjustedTotal());
            assertEquals(BigDecimal.ZERO, warning.realisticCap());
        }

        @Test
        void returnsNoneWhenProfileHasNoUsableCapacityData() {
            // weeksAnalyzed == 0 → profile not actionable → NONE
            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.DELIVERY, "8.0")),
                    profile(new BigDecimal("1.20"), new BigDecimal("18.0"), "[]", 0));

            assertEquals(OvercommitLevel.NONE, warning.level());
            assertEquals(new BigDecimal("0"), warning.adjustedTotal());
            assertEquals(new BigDecimal("0"), warning.realisticCap());
            assertEquals("", warning.message());
        }

        // ── MODERATE level ────────────────────────────────────────────────────

        @Test
        void returnsModerateWhenAdjustedTotalBetweenCapAndCapTimesTwentyPercent() {
            // 15h * 1.1 = 16.5h; cap=15.0h; cap*1.2=18.0h; 16.5 in (15,18) → MODERATE
            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.1"),
                    new BigDecimal("15.0"),
                    "[]",
                    4);

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.DELIVERY, "15.0")), profile);

            assertEquals(OvercommitLevel.MODERATE, warning.level());
            assertEquals(new BigDecimal("16.5"), warning.adjustedTotal());
            assertEquals(new BigDecimal("15.0"), warning.realisticCap());
            assertTrue(warning.message().contains("16.5 h"),
                    "MODERATE message should include adjusted total hours");
            assertTrue(warning.message().contains("15.0 h"),
                    "MODERATE message should include realistic cap hours");
        }

        // ── HIGH level ────────────────────────────────────────────────────────

        @Test
        void returnsHighWhenAdjustedTotalExceedsCapByMoreThanTwentyPercent() {
            // DELIVERY 10h * 1.50 bias = 15h; PEOPLE 10h * 1.10 global = 11h; total=26h
            // cap=20h; cap*1.2=24h; 26 > 24 → HIGH
            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.10"),
                    new BigDecimal("20.0"),
                    "[{\"category\":\"DELIVERY\",\"bias\":1.50}]",
                    6);

            List<WeeklyCommitEntity> commits = List.of(
                    commit(CommitCategory.DELIVERY, "10.0"),
                    commit(CommitCategory.PEOPLE, "10.0"));

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(commits, profile);

            assertEquals(OvercommitLevel.HIGH, warning.level());
            assertEquals(new BigDecimal("26.0"), warning.adjustedTotal());
            assertEquals(new BigDecimal("20.0"), warning.realisticCap());
            assertTrue(warning.message().contains("26.0 h"),
                    "HIGH message should include adjusted total hours");
            assertTrue(warning.message().contains("20.0 h"),
                    "HIGH message should include realistic cap hours");
        }

        // ── Per-category bias ─────────────────────────────────────────────────

        @Test
        void perCategoryBiasForDeliveryChangesOvercommitLevel() {
            // cap=20h, globalBias=1.0 (no effect on its own)
            // DELIVERY commit 18h: without category bias → 18h*1.0=18h < 20h → NONE
            // With DELIVERY bias=1.3 → 18h*1.3=23.4h > 20h but < 24h (20*1.2) → MODERATE
            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.0"),
                    new BigDecimal("20.0"),
                    "[{\"category\":\"DELIVERY\",\"bias\":1.30}]",
                    4);

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.DELIVERY, "18.0")), profile);

            assertEquals(OvercommitLevel.MODERATE, warning.level(),
                    "DELIVERY category bias of 1.3 should push 18h past the 20h cap");
            assertEquals(new BigDecimal("23.4"), warning.adjustedTotal());
        }

        // ── Fallback to global bias ───────────────────────────────────────────

        @Test
        void fallsBackToOverallEstimationBiasWhenCategoryNotInBiasMap() {
            // Bias map has DELIVERY=0.80 only; CUSTOMER commit falls back to globalBias=1.1
            // 20h * 1.1 = 22.0h; cap=20h; cap*1.2=24h; 22.0 in (20,24) → MODERATE
            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.1"),
                    new BigDecimal("20.0"),
                    "[{\"category\":\"DELIVERY\",\"bias\":0.80}]",
                    4);

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.CUSTOMER, "20.0")), profile);

            assertEquals(OvercommitLevel.MODERATE, warning.level());
            assertEquals(new BigDecimal("22.0"), warning.adjustedTotal());
            assertEquals(new BigDecimal("20.0"), warning.realisticCap());
        }

        @Test
        void fallsBackToGlobalBiasWhenCategoryBiasJsonCannotBeParsed() {
            // JSON is malformed → category bias map is empty → falls back to globalBias=1.1
            // 20h * 1.1 = 22.0h → MODERATE
            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.10"),
                    new BigDecimal("20.0"),
                    "not-json",
                    4);

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(commit(CommitCategory.CUSTOMER, "20.0")), profile);

            assertEquals(OvercommitLevel.MODERATE, warning.level());
            assertEquals(new BigDecimal("22.0"), warning.adjustedTotal());
            assertEquals(new BigDecimal("20.0"), warning.realisticCap());
        }

        // ── Null estimated hours ──────────────────────────────────────────────

        @Test
        void commitsWithNoEstimatedHoursContributeZeroToAdjustedTotal() {
            // One commit with 10h, one commit with null hours → total = 10.0h < cap 20h → NONE
            WeeklyCommitEntity withHours = commit(CommitCategory.DELIVERY, "10.0");
            WeeklyCommitEntity withoutHours = new WeeklyCommitEntity(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "No-hours commit");
            withoutHours.setCategory(CommitCategory.DELIVERY);
            // estimatedHours is null by default; the detector should skip it

            CapacityProfileEntity profile = profile(
                    new BigDecimal("1.0"),
                    new BigDecimal("20.0"),
                    "[]",
                    4);

            OvercommitWarning warning = overcommitDetector.detectOvercommitment(
                    List.of(withHours, withoutHours), profile);

            assertEquals(OvercommitLevel.NONE, warning.level());
            assertEquals(new BigDecimal("10.0"), warning.adjustedTotal(),
                    "The commit with null hours must contribute zero to the adjusted total");
        }
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    private static WeeklyCommitEntity commit(CommitCategory category, String estimatedHours) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Commit");
        commit.setCategory(category);
        commit.setEstimatedHours(new BigDecimal(estimatedHours));
        return commit;
    }

    private static CapacityProfileEntity profile(
            BigDecimal estimationBias,
            BigDecimal realisticCap,
            String categoryBiasJson,
            int weeksAnalyzed) {
        CapacityProfileEntity profile = new CapacityProfileEntity(UUID.randomUUID(), UUID.randomUUID());
        profile.setEstimationBias(estimationBias);
        profile.setRealisticWeeklyCap(realisticCap);
        profile.setCategoryBiasJson(categoryBiasJson);
        profile.setWeeksAnalyzed(weeksAnalyzed);
        return profile;
    }
}
