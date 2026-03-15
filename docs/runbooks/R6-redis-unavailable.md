# R6: Redis Unavailable

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_Redis_Unavailable` (P2)

## Impact

The system degrades gracefully when Redis is down (PRD §9.2):
- Direct upstream calls replace cached data (RCDO, org graph)
- In-process Caffeine cache provides short-term stopgap (60s TTL)
- AI suggestion caching is skipped (requests go directly to LLM)
- **No data loss** — Redis is cache only

## Triage steps

1. **Check ElastiCache console.**
   - Is the node healthy? Rebooting? In maintenance window?
   - Status: `aws elasticache describe-cache-clusters --cache-cluster-id wc-prod-redis`

2. **Check security group rules.**
   - Can weekly-service reach Redis on port 6379?
   - Verify the security group allows traffic from the ECS task security group

3. **Check Redis memory.**
   - `redis-cli INFO memory`
   - Is eviction happening aggressively? High memory fragmentation?

4. **Check Redis connection count.**
   - `redis-cli INFO clients`
   - If max connections reached, check for connection leaks in the application

## Resolution

- If Redis is unrecoverable: failover to replica (if configured) or provision a new node
- Cache will warm organically from upstream calls
- No immediate user action needed — the system continues to function
