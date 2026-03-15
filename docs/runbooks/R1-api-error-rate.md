# R1: API Error Rate Elevated

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_API_ErrorRate_FastBurn` (P1), `WC_API_ErrorRate_SlowBurn` (P2)

## Triage steps

1. **Check which endpoints are erroring.**
   - Grafana → WC Service Health → error rate by endpoint panel
   - `sum by (uri) (rate(http_server_requests_seconds_count{status=~"5..",application="weekly-service"}[5m]))`

2. **Check recent deploys.**
   - Was there a deployment in the last 2 hours?
   - If yes, consider immediate rollback: `./scripts/rollback.sh weekly-service <previous-version>`

3. **Check Postgres connectivity.**
   - `curl http://<service-host>:8080/actuator/health`
   - Look for `db.status` in the response

4. **Check for query timeouts in slow query log.**
   - CloudWatch Logs → filter by `"level":"ERROR"` and `"orgId"`
   - Postgres: `SELECT * FROM pg_stat_activity WHERE state = 'active' AND query_start < now() - interval '5 seconds'`

5. **Check upstream dependency health.**
   - RCDO service status
   - Org graph API status
   - LLM provider status page

6. **If isolated to one endpoint:**
   - Filter logs by `correlationId` to trace the request
   - Check for bad input patterns

7. **If widespread (all endpoints):**
   - Check ECS task health and resource metrics (OOM? CPU throttle?)
   - `aws ecs describe-tasks --cluster wc-prod --tasks <task-id>`

8. **Escalate to L2 if not resolved within 30 minutes.**

## Rollback procedure

```bash
# Revert to previous known-good version
./scripts/rollback.sh weekly-service <previous-image-tag>
```
