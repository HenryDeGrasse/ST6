package com.weekly.plan.service;

import java.util.UUID;

/**
 * Thrown when an optimistic lock conflict is detected.
 */
public class OptimisticLockException extends RuntimeException {

    private final UUID entityId;
    private final int expectedVersion;
    private final int actualVersion;

    public OptimisticLockException(UUID entityId, int expectedVersion, int actualVersion) {
        super("Version conflict on entity " + entityId
                + ": expected " + expectedVersion + ", actual " + actualVersion);
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public int getActualVersion() {
        return actualVersion;
    }
}
