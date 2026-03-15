# R4: Notification Delivery Failure / DLQ Accumulation

**Last reviewed:** 2026-03-13  
**Triggered by:** `WC_Notification_DLQ_Depth` (P3), `WC_Notification_DeliveryFailRate` (P3)

## Impact

In-app notifications stop appearing for users. Workflow is unaffected —
plans can still be created, locked, and reconciled. Notifications are
convenience signals (review pending, changes requested, etc.).

## Triage steps

1. **Check DLQ depth.**
   - CloudWatch Metrics → SQS → `weekly-notifications-dlq` → `ApproximateNumberOfMessagesVisible`
   - If depth is growing: messages are failing repeatedly

2. **Inspect DLQ messages.**
   ```bash
   aws sqs receive-message \
     --queue-url <DLQ_URL> \
     --max-number-of-messages 5 \
     --attribute-names All
   ```
   - Look at the `Body` field for the failing event payload
   - Check `ApproximateReceiveCount` — if it's at the max retries, the message was retried to exhaustion

3. **Check the notification materializer logs.**
   - Filter by `"component":"NotificationMaterializer"` and `"level":"ERROR"`
   - Common causes:
     - Missing user in org graph → `UserNotFoundException`
     - Invalid event type → deserialization failure
     - Database connectivity issue → `DataAccessException`

4. **Check outbox poller health.**
   - Grafana → WC Service Health → outbox publish rate
   - If outbox publish rate is zero but outbox table is growing → poller is stuck
   - Restart the service if the poller thread is dead

5. **Check SQS/SNS permissions.**
   - Verify IAM role allows `sqs:SendMessage` to the notification queue
   - Verify SNS topic subscription is active

6. **If Postgres is the bottleneck:**
   - Check active connections: `SELECT count(*) FROM pg_stat_activity WHERE application_name LIKE '%weekly%';`
   - Check for lock contention on `notification` table

## Resolution

- **DLQ messages from transient failures:** Redrive from DLQ to main queue once root cause is fixed
  ```bash
  aws sqs start-message-move-task \
    --source-arn <DLQ_ARN> \
    --destination-arn <MAIN_QUEUE_ARN>
  ```
- **Serialization/schema mismatch:** Deploy fix, then redrive
- **Persistent DB issues:** Escalate to DBA / infrastructure team

## Prevention

- Monitor DLQ depth with a threshold alert (> 10 messages)
- Ensure notification materializer has retry with exponential backoff
- Keep event schema backward-compatible (outbox pattern)
