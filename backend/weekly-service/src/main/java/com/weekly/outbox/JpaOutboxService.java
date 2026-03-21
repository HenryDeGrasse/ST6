package com.weekly.outbox;

import com.weekly.shared.EventType;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * JPA-backed implementation of {@link OutboxService}.
 *
 * <p>Writes outbox events to the database in the current transaction.
 */
@Service
public class JpaOutboxService implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public JpaOutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void publish(
            EventType eventType,
            String aggregateType,
            UUID aggregateId,
            UUID orgId,
            Map<String, Object> payload
    ) {
        OutboxEventEntity event = new OutboxEventEntity(
                eventType.getValue(),
                aggregateType,
                aggregateId,
                orgId,
                payload
        );
        outboxEventRepository.save(event);
    }
}
