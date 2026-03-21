# R11: GDPR Right-to-Be-Forgotten (Data Deletion)

**Last reviewed:** 2026-03-18
**Triggered by:** User deletion request (email/ticket to DPO), regulatory obligation (GDPR Art. 17)

---

## Overview

The GDPR right-to-be-forgotten process erases all personal data for a given user
within their organisation. For MVP this is a manual process: an administrator calls
the endpoint described below.

The endpoint:

1. **Soft-deletes** all `weekly_plans` owned by the user (sets `deleted_at = NOW()`).
2. **Soft-deletes** all `weekly_commits` belonging to those plans.
3. **Anonymises** all `audit_events` where the user is the actor — the
   `actor_user_id` is replaced with a deterministic SHA-256-derived UUID.
4. **Re-hashes** the existing audit chain for the org so the sanctioned
   anonymisation does not appear as tampering.
5. **Hard-deletes** all `notifications` for the user.
6. **Hard-deletes** all `idempotency_keys` for the user.
7. **Records an audit event** for the deletion request itself (actor = admin).

All steps run atomically in a single database transaction.

---

## Prerequisites

| Requirement | Details |
|---|---|
| Role | Caller must have the `ADMIN` role in their JWT / dev token |
| Org scope | Deletion is scoped to the admin's `orgId` (from the token) |
| User ID | UUID of the user to be erased |

---

## Step-by-step

### 1 — Identify the user's UUID

Look up the user's `userId` UUID in the identity provider (PA IdP) or the
`audit_events` table:

```sql
SELECT DISTINCT actor_user_id
FROM audit_events
WHERE org_id = '<org-id>'
-- narrow further if needed:
  AND created_at > NOW() - INTERVAL '30 days';
```

### 2 — Call the deletion endpoint

```bash
curl -X DELETE \
  https://<service-host>/api/v1/admin/users/<user-uuid>/data \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "X-Request-Id: $(uuidgen)"
```

Expected response: **HTTP 204 No Content** with an empty body.

For local / dev environments using the dev-auth header format:

```bash
curl -X DELETE \
  http://localhost:8080/api/v1/admin/users/<user-uuid>/data \
  -H "X-User-Id: <admin-uuid>" \
  -H "X-Org-Id: <org-uuid>" \
  -H "X-Roles: ADMIN"
```

### 3 — Verify completion

#### Plans and commits (should be hidden / soft-deleted)

```sql
-- These should return 0 rows when checking for still-active records
SELECT count(*) FROM weekly_plans
WHERE org_id = '<org-id>'
  AND owner_user_id = '<user-uuid>'
  AND deleted_at IS NULL;

SELECT count(*) FROM weekly_commits wc
JOIN weekly_plans wp ON wc.weekly_plan_id = wp.id
WHERE wp.org_id = '<org-id>'
  AND wp.owner_user_id = '<user-uuid>'
  AND wc.deleted_at IS NULL;

-- These should return the soft-deleted rows
SELECT count(*) FROM weekly_plans
WHERE org_id = '<org-id>' AND owner_user_id = '<user-uuid>'
  AND deleted_at IS NOT NULL;

SELECT count(*) FROM weekly_commits wc
JOIN weekly_plans wp ON wc.weekly_plan_id = wp.id
WHERE wp.org_id = '<org-id>'
  AND wp.owner_user_id = '<user-uuid>'
  AND wc.deleted_at IS NOT NULL;
```

#### Audit events (should be anonymised, not deleted)

```sql
-- The original user ID should no longer appear
SELECT count(*) FROM audit_events
WHERE org_id = '<org-id>' AND actor_user_id = '<user-uuid>';
-- Expected: 0

-- Anonymised rows are still present under the SHA-256-derived UUID
-- (see service code for derivation)
```

#### Notifications and idempotency keys (should be gone)

```sql
SELECT count(*) FROM notifications
WHERE org_id = '<org-id>' AND user_id = '<user-uuid>';
-- Expected: 0

SELECT count(*) FROM idempotency_keys
WHERE org_id = '<org-id>' AND user_id = '<user-uuid>';
-- Expected: 0
```

#### Audit trail for the deletion itself

```sql
SELECT * FROM audit_events
WHERE org_id = '<org-id>'
  AND action = 'USER_DATA_DELETED'
  AND aggregate_id = '<user-uuid>'
ORDER BY created_at DESC
LIMIT 5;
```

---

## Error handling

| HTTP Status | Meaning | Action |
|---|---|---|
| 204 No Content | Success | Proceed to verification steps |
| 401 Unauthorized | Missing / invalid credentials | Check JWT / dev headers |
| 403 Forbidden | Caller lacks ADMIN role | Ensure the token includes the `ADMIN` role |
| 500 Internal Server Error | Transaction rolled back | Check service logs for root cause; the deletion is **atomic** — retry is safe |

---

## Idempotency

The operation is safe to retry. Soft-deletes are gated on `deleted_at IS NULL`,
so re-running against already-soft-deleted rows is a no-op. Audit anonymisation
uses `actor_user_id = <original-uuid>` in the `WHERE` clause, so re-running
after the first anonymisation also affects 0 rows. Notifications and idempotency
keys are already absent after the first successful run.

---

## Escalation

- If the endpoint returns 500, check application logs for the root cause before retrying.
- If rows remain after the operation (verification query returns > 0), escalate to
  the on-call backend engineer and the Data Protection Officer (DPO).
- For hard-delete of soft-deleted plan rows, the existing
  `PlanRetentionJob` (PRD §14.7) will permanently remove them after the configured
  grace period (default 90 days). To expedite permanent deletion, contact the DBA
  team to run a manual hard-delete.
