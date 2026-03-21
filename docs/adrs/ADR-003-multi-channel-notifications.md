# ADR-003: Multi-Channel Notification Delivery

**Date:** 2026-03-18
**Status:** Proposed

## Context

The current notification system delivers all notifications in-app only (§4). Users receive alerts within the WorkCycle application but have no option to receive notifications via email or Slack, even for high-priority events such as manager review requests, plan lock deadlines, or reconciliation reminders.

**Trigger (per §17.3.3):** Phase 1 rollout feedback confirms demand for push notifications outside the app. This is expected during months 2–3, when teams begin relying on WorkCycle as their primary alignment tool and need to be reached when not actively using the application.

Current state:
- All notifications are delivered in-app via the notification worker (Container 5, §9.2).
- The notification worker consumes outbox events and creates `notifications` records.
- No email or Slack delivery adapters exist.
- No user notification preferences are stored.

## Decision

Extend the notification worker with two new output adapters — email and Slack — and introduce a user notification preferences model to control routing.

Specifically:
- Add an **email adapter** using SES or the PA platform's existing email service.
- Add a **Slack adapter** using Slack webhooks or the Slack bot API.
- Introduce a `notification_preferences` table: `(user_id, org_id, channel [in_app | email | slack], enabled)` to store per-user, per-channel delivery preferences.
- Notification routing logic in the worker reads the user's preferences and delivers to all enabled channels.
- Feature flags `wc.notifications.email` and `wc.notifications.slack` (§13.7) control availability at the org/product level.
- Outbox event contracts remain unchanged — the worker consumes the same events and decides the delivery channel based on user preferences.
- The in-app notification path is unchanged and remains the default for all users.

## Consequences

**Benefits:**
- Meets user demand for out-of-app notifications, increasing engagement and reducing missed deadlines.
- Feature-flagged rollout allows safe, incremental enablement per org.
- Adapter pattern keeps email/Slack implementation isolated from core notification logic.
- Outbox event contracts are unchanged — no upstream changes required.
- Foundation for the notification service extraction (ADR-007) in H2.

**Trade-offs:**
- Introduces external service dependencies (SES, Slack API) that must be monitored for availability and delivery success rates.
- Email and Slack delivery can fail silently from the user's perspective — requires robust error logging, retry logic, and alerting on delivery failure rates.
- User preference management adds a new data model and UI surface (notification settings page).
- Slack integration requires OAuth app setup and workspace installation, adding operational complexity.
- Volume of email/Slack messages must be carefully managed to avoid user fatigue — notification frequency and batching strategy should be defined before rollout.

**Related:** This is a prerequisite for the notification service extraction (ADR-007) in H2, which will split the multi-channel delivery logic into an independent service. The adapter pattern chosen here is designed to make that extraction straightforward.
