# R3: Outbox Lag / Stall

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_Outbox_Lag` (P2), `WC_Outbox_Stall` (P1)

## Triage steps

1. **Check if outbox poller ECS task is running.**
   ```bash
   aws ecs describe-tasks --cluster wc-prod --tasks $(aws ecs list-tasks --cluster wc-prod --service-name wc-outbox-poller --query 'taskArns[0]' --output text)
   ```
   - If crashed → restart the task

2. **Check outbox table directly.**
   ```sql
   SELECT count(*) FROM outbox_events WHERE published_at IS NULL;
   SELECT min(occurred_at) FROM outbox_events WHERE published_at IS NULL;
   ```

3. **Check message bus health (SQS).**
   - Is the queue accessible? Check permissions.
   - `aws sqs get-queue-attributes --queue-url <queue-url> --attribute-names All`

4. **If poller is running but not publishing:**
   - Check logs for errors (serialization failure? network issue to SQS?)
   - Filter: `"logger":"com.weekly.outbox" AND "level":"ERROR"`

5. **If queue is full or throttled:**
   - Check SQS metrics in CloudWatch
   - SQS standard queues have effectively unlimited throughput

6. **Emergency manual drain (notifications only, not data events):**
   ```sql
   -- Only use if events are confirmed already processed or safe to skip
   UPDATE outbox_events
   SET published_at = now()
   WHERE published_at IS NULL
     AND occurred_at < now() - interval '1 hour';
   ```

## Recovery

Events that were in the outbox during a stall will be published when the poller recovers. No manual action needed if the poller is restarted. Notifications may be delayed but are not lost.
