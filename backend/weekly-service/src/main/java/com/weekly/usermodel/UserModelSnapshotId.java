package com.weekly.usermodel;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link UserModelSnapshotEntity}.
 *
 * <p>Used by JPA's {@code @IdClass} mechanism to represent the
 * composite key {@code (org_id, user_id)} on the
 * {@code user_model_snapshots} table.
 */
public class UserModelSnapshotId implements Serializable {

    private UUID orgId;
    private UUID userId;

    /** No-arg constructor required by JPA. */
    public UserModelSnapshotId() {
    }

    public UserModelSnapshotId(UUID orgId, UUID userId) {
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
        if (!(o instanceof UserModelSnapshotId)) {
            return false;
        }
        UserModelSnapshotId other = (UserModelSnapshotId) o;
        return Objects.equals(orgId, other.orgId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId);
    }
}
