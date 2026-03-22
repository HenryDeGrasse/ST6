package com.weekly.forecast;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link LatestForecastEntity}.
 */
public class LatestForecastId implements Serializable {

    private UUID orgId;
    private UUID outcomeId;

    public LatestForecastId() {
    }

    public LatestForecastId(UUID orgId, UUID outcomeId) {
        this.orgId = orgId;
        this.outcomeId = outcomeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LatestForecastId that)) {
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
