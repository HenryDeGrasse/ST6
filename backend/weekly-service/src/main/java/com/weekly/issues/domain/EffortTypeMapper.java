package com.weekly.issues.domain;

import com.weekly.plan.domain.CommitCategory;

/**
 * Utility class for mapping legacy {@link CommitCategory} values to the new
 * {@link EffortType} classification (Phase 6).
 *
 * <p>Mapping:
 * <ul>
 *   <li>DELIVERY / GTM → BUILD</li>
 *   <li>OPERATIONS / TECH_DEBT → MAINTAIN</li>
 *   <li>CUSTOMER / PEOPLE → COLLABORATE</li>
 *   <li>LEARNING → LEARN</li>
 *   <li>null → null</li>
 * </ul>
 */
public final class EffortTypeMapper {

    private EffortTypeMapper() {
        // utility class
    }

    /**
     * Maps a {@link CommitCategory} to the equivalent {@link EffortType}.
     *
     * @param category the commit category (may be {@code null})
     * @return the corresponding effort type, or {@code null} if {@code category} is {@code null}
     */
    public static EffortType fromCommitCategory(CommitCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case DELIVERY, GTM -> EffortType.BUILD;
            case OPERATIONS, TECH_DEBT -> EffortType.MAINTAIN;
            case CUSTOMER, PEOPLE -> EffortType.COLLABORATE;
            case LEARNING -> EffortType.LEARN;
        };
    }
}
