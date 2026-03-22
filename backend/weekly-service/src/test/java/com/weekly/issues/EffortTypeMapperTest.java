package com.weekly.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.EffortTypeMapper;
import com.weekly.plan.domain.CommitCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link EffortTypeMapper}: all 7 {@link CommitCategory} → 4 {@link EffortType}
 * mappings, plus null input.
 */
class EffortTypeMapperTest {

    // ── Explicit per-category tests ───────────────────────────────────────────

    @Test
    void deliveryMapsToBuild() {
        assertEquals(EffortType.BUILD, EffortTypeMapper.fromCommitCategory(CommitCategory.DELIVERY));
    }

    @Test
    void gtmMapsToBuild() {
        assertEquals(EffortType.BUILD, EffortTypeMapper.fromCommitCategory(CommitCategory.GTM));
    }

    @Test
    void operationsMapsToMaintain() {
        assertEquals(EffortType.MAINTAIN, EffortTypeMapper.fromCommitCategory(CommitCategory.OPERATIONS));
    }

    @Test
    void techDebtMapsToMaintain() {
        assertEquals(EffortType.MAINTAIN, EffortTypeMapper.fromCommitCategory(CommitCategory.TECH_DEBT));
    }

    @Test
    void customerMapsToCollaborate() {
        assertEquals(EffortType.COLLABORATE, EffortTypeMapper.fromCommitCategory(CommitCategory.CUSTOMER));
    }

    @Test
    void peopleMapsToCollaborate() {
        assertEquals(EffortType.COLLABORATE, EffortTypeMapper.fromCommitCategory(CommitCategory.PEOPLE));
    }

    @Test
    void learningMapsToLearn() {
        assertEquals(EffortType.LEARN, EffortTypeMapper.fromCommitCategory(CommitCategory.LEARNING));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(EffortTypeMapper.fromCommitCategory(null));
    }

    // ── Parameterised coverage: all 7 categories ─────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "DELIVERY,   BUILD",
        "GTM,        BUILD",
        "OPERATIONS, MAINTAIN",
        "TECH_DEBT,  MAINTAIN",
        "CUSTOMER,   COLLABORATE",
        "PEOPLE,     COLLABORATE",
        "LEARNING,   LEARN"
    })
    void allCategoriesMapCorrectly(String categoryName, String expectedEffortType) {
        CommitCategory category = CommitCategory.valueOf(categoryName.strip());
        EffortType expected = EffortType.valueOf(expectedEffortType.strip());

        assertEquals(expected, EffortTypeMapper.fromCommitCategory(category));
    }

    // ── Coverage: every CommitCategory value has a non-null mapping ───────────

    @Test
    void allCommitCategoryValuesHaveANonNullMapping() {
        for (CommitCategory category : CommitCategory.values()) {
            EffortType result = EffortTypeMapper.fromCommitCategory(category);
            org.junit.jupiter.api.Assertions.assertNotNull(result,
                    "EffortTypeMapper.fromCommitCategory(" + category + ") must not return null");
        }
    }
}
