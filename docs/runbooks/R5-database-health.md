# R5: Database Health Issues

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_DB_ConnectionPool_High` (P2)

## Triage steps

1. **Check long-running queries.**
   ```sql
   SELECT pid, usename, state, query_start, now() - query_start AS duration, query
   FROM pg_stat_activity
   WHERE state != 'idle' AND query_start < now() - interval '10 seconds'
   ORDER BY duration DESC;
   ```
   - Kill if appropriate: `SELECT pg_terminate_backend(<pid>);`

2. **Check HikariCP metrics.**
   - Active connections growing but never returning → connection leak
   - Look for missing `@Transactional` or unclosed connections in code

3. **Check RDS metrics.**
   - CPU, IOPS, freeable memory in CloudWatch
   - If resource-constrained, consider scaling the instance class

4. **Check for blocked queries.**
   ```sql
   SELECT blocked.pid AS blocked_pid,
          blocking.pid AS blocking_pid,
          blocked.query AS blocked_query,
          blocking.query AS blocking_query
   FROM pg_stat_activity blocked
   JOIN pg_locks blocked_locks ON blocked.pid = blocked_locks.pid
   JOIN pg_locks blocking_locks ON blocked_locks.locktype = blocking_locks.locktype
     AND blocked_locks.relation = blocking_locks.relation
     AND blocked_locks.pid != blocking_locks.pid
   JOIN pg_stat_activity blocking ON blocking_locks.pid = blocking.pid
   WHERE NOT blocked_locks.granted;
   ```

## Resolution

- Connection leak: identify and fix the code path, deploy hotfix
- Resource exhaustion: scale RDS instance (requires maintenance window for non-Multi-AZ)
- Long-running query: optimize or add appropriate timeout
