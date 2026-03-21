package com.weekly.urgency;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key class for {@link OutcomeMetadataEntity}.
 *
 * <p>Maps to the composite PK {@code (org_id, outcome_id)} in the
 * {@code outcome_metadata} table.
 */
public class OutcomeMetadataId implements Serializable {

    private UUID orgId;
    private UUID outcomeId;

    protected OutcomeMetadataId() {
        // JPA
    }

    public OutcomeMetadataId(UUID orgId, UUID outcomeId) {
        this.orgId = orgId;
        this.outcomeId = outcomeId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getOutcomeId() {
        return outcomeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutcomeMetadataId that)) {
            return false;
        }
        return Objects.equals(orgId, that.orgId)
                && Objects.equals(outcomeId, that.outcomeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, outcomeId);
    }
}
