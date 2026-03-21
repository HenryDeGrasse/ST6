package com.weekly.outbox;

import com.weekly.shared.EventType;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes domain events to the transactional outbox.
 *
 * <p>Called within the same DB transaction as the domain write,
 * ensuring atomicity between state changes and event publication.
 */
public interface OutboxService {

    /**
     * Writes an outbox event in the current transaction.
     *
     * @param eventType     the domain event type
     * @param aggregateType the aggregate type (e.g., "WeeklyPlan")
     * @param aggregateId   the aggregate ID
     * @param orgId         the organization ID
     * @param payload       the event payload
     */
    void publish(
            EventType eventType,
            String aggregateType,
            UUID aggregateId,
            UUID orgId,
            Map<String, Object> payload
    );
}
