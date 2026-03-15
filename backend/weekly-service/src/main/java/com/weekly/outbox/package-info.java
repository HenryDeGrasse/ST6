/**
 * Transactional outbox infrastructure for event-driven integrations.
 *
 * <h2>Overview</h2>
 * <p>This package implements the <em>transactional outbox pattern</em>: every domain
 * state transition (plan created, committed, locked, reconciled, reviewed, etc.)
 * writes an {@link com.weekly.outbox.OutboxEventEntity} row in the same database
 * transaction as the domain write. This guarantees at-least-once delivery semantics
 * without two-phase commits or distributed transactions.
 *
 * <h2>Current state (MVP)</h2>
 * <p>The outbox is <strong>pre-built infrastructure</strong>. Events are written
 * transactionally by {@link com.weekly.outbox.OutboxService} (implemented by
 * {@link com.weekly.outbox.JpaOutboxService}) on every state transition, but
 * <strong>no external consumer exists yet</strong>. The internal
 * {@link com.weekly.notification.NotificationMaterializer} polls unpublished events
 * to materialize in-app notifications and marks them as published, but this is an
 * in-process consumer — not an external integration.
 *
 * <p>The {@code published_at} column remains {@code NULL} until a consumer
 * (NotificationMaterializer, CDC connector, SQS fan-out, etc.) processes and
 * explicitly marks the event. This is intentional — setting {@code published_at}
 * without actual delivery would be semantically incorrect.
 *
 * <h2>Future integrations</h2>
 * <p>Planned consumers include:
 * <ul>
 *   <li><strong>CDC (Change Data Capture)</strong> — Debezium connector tailing the
 *       {@code outbox_events} table and publishing to Kafka/SNS.</li>
 *   <li><strong>SQS fan-out</strong> — a poller that reads unpublished events and
 *       pushes them to SQS for downstream microservices.</li>
 *   <li><strong>Webhook delivery</strong> — HTTP callbacks to registered endpoints.</li>
 * </ul>
 *
 * <h2>Retention</h2>
 * <p>{@link com.weekly.outbox.OutboxRetentionJob} runs on a schedule and deletes
 * outbox events older than a configurable retention period (default 30 days).
 * This prevents unbounded table growth without lying about publication status.
 * The job is enabled in {@code local} and {@code dev} profiles and disabled in
 * {@code test} to avoid interfering with test assertions.
 *
 * @see com.weekly.outbox.OutboxService
 * @see com.weekly.outbox.OutboxRetentionJob
 * @see com.weekly.notification.NotificationMaterializer
 */
package com.weekly.outbox;
