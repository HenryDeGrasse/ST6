package com.weekly.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ReviewStatus;
import org.junit.jupiter.api.Test;

/**
 * Verifies that domain enums match the PRD definitions and stay
 * in sync with the TypeScript contracts and DB CHECK constraints.
 *
 * <p>If these tests break, it means the backend enum drifted from
 * the contract. Update both sides and the migration together.
 */
class PlanDomainEnumTest {

    @Test
    void planStateMatchesContractValues() {
        String[] expected = {"DRAFT", "LOCKED", "RECONCILING", "RECONCILED", "CARRY_FORWARD"};
        String[] actual = java.util.Arrays.stream(PlanState.values())
                .map(Enum::name).toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void reviewStatusMatchesContractValues() {
        String[] expected = {"REVIEW_NOT_APPLICABLE", "REVIEW_PENDING",
                "CHANGES_REQUESTED", "APPROVED"};
        String[] actual = java.util.Arrays.stream(ReviewStatus.values())
                .map(Enum::name).toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void chessPriorityMatchesContractValues() {
        String[] expected = {"KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"};
        String[] actual = java.util.Arrays.stream(ChessPriority.values())
                .map(Enum::name).toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void completionStatusMatchesContractValues() {
        String[] expected = {"DONE", "PARTIALLY", "NOT_DONE", "DROPPED"};
        String[] actual = java.util.Arrays.stream(CompletionStatus.values())
                .map(Enum::name).toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void lockTypeMatchesContractValues() {
        String[] expected = {"ON_TIME", "LATE_LOCK"};
        String[] actual = java.util.Arrays.stream(LockType.values())
                .map(Enum::name).toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void commitCategoryMatchesContractValues() {
        String[] expected = {"DELIVERY", "OPERATIONS", "CUSTOMER", "GTM",
                "PEOPLE", "LEARNING", "TECH_DEBT"};
        String[] actual = java.util.Arrays.stream(CommitCategory.values())
                .map(Enum::name).toArray(String[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void chessPriorityHasSixPieces() {
        assertEquals(6, ChessPriority.values().length);
    }

    @Test
    void commitCategoryHasSevenCategories() {
        assertEquals(7, CommitCategory.values().length);
    }
}
