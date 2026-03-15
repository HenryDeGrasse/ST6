# R2: API Latency Elevated

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_API_Latency_CRUD` (P2), `WC_API_Latency_Dashboard` (P2)

## Triage steps

1. **Check DB query latency.**
   - Grafana → WC Infrastructure → RDS IOPS, connections
   - HikariCP pool utilization (> 80% = bottleneck)

2. **Check Redis latency and connection errors.**
   - Is there a cache miss storm? (burst of RCDO or org graph cache misses)
   - `redis-cli INFO stats | grep keyspace`

3. **Check for slow queries.**
   - Postgres: `SELECT pid, query, now() - query_start AS duration FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC LIMIT 10`

4. **Check if a migration recently ran.**
   - Index rebuild or large data migration could cause temporary degradation

5. **If dashboard-specific latency:**
   - Check query plan for team summary endpoint
   - Consider adding missing index or reviewing query filters
   - `EXPLAIN ANALYZE SELECT ... FROM weekly_plans WHERE org_id = ? AND week_start_date = ? ...`

6. **If AI endpoint latency:**
   - See R7 (LLM timeout runbook)
   - Check LLM provider status

## Resolution

- If slow query identified: optimize query or add index (requires migration)
- If connection pool exhausted: check for leaked connections or increase pool size
- If cache-related: verify Redis health and flush stale entries if needed
