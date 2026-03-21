package com.weekly.capacity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key class for {@link CapacityProfileEntity}.
 *
 * <p>Maps to the composite PK {@code (org_id, user_id)} in the
 * {@code user_capacity_profiles} table.
 */
public class CapacityProfileId implements Serializable {

    private UUID orgId;
    private UUID userId;

    protected CapacityProfileId() {
        // JPA
    }

    public CapacityProfileId(UUID orgId, UUID userId) {
        this.orgId = orgId;
        this.userId = userId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CapacityProfileId that)) {
            return false;
        }
        return Objects.equals(orgId, that.orgId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId);
    }
}
