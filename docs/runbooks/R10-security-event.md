# R10: Security Event Detected

**Last reviewed:** 2026-03-12  
**Triggered by:** `WC_Auth_FailureSpike` (P2), RLS violation logs (P1)

## RLS Violation (P1 — Immediate)

1. **Check the violating query in Postgres logs.**
   - Filter: `rls_violation` or `insufficient_privilege` on data tables

2. **Identify the code path.**
   - This may indicate a bug that bypasses application-level `org_id` filtering

3. **Assess blast radius:**
   - Was cross-tenant data actually returned? (RLS should prevent this)
   - If RLS caught it (no data returned): fix the application bug urgently
   - If data was returned: invoke data breach response process

## Auth Failure Spike (P2)

1. **Identify the pattern.**
   ```
   # Check if it's a single user or distributed
   Filter logs: status=403 | group by userId
   Filter logs: status=403 | group by sourceIP
   ```

2. **Single user with many 403s:**
   - Likely misconfigured account or expired permissions
   - Check user's roles and reporting relationships
   - Contact the user

3. **Distributed 403s from same IP:**
   - Possible brute-force or enumeration attack
   - Coordinate with PA identity team for IP-level blocking
   - Check if failures correspond to real attack or misconfigured integration

4. **Burst of 403s after a deploy:**
   - Likely a regression in authorization logic
   - Check recent code changes to auth module
   - Consider rollback if authorization is broadly broken

## Escalation

- RLS violation: always escalate to tech lead and security channel immediately
- Auth spike: escalate if pattern suggests intentional abuse
