/**
 * Domain event types from the PRD (Appendix B).
 *
 * All events follow the outbox schema with schemaVersion: 1.
 */
export enum EventType {
  PLAN_CREATED = "plan.created",
  PLAN_LOCKED = "plan.locked",
  PLAN_RECONCILIATION_STARTED = "plan.reconciliation_started",
  PLAN_RECONCILED = "plan.reconciled",
  PLAN_CARRY_FORWARD = "plan.carry_forward",
  REVIEW_SUBMITTED = "review.submitted",
  COMMIT_CREATED = "commit.created",
  COMMIT_UPDATED = "commit.updated",
  COMMIT_DELETED = "commit.deleted",
  COMMIT_ACTUAL_UPDATED = "commit.actual_updated",
  /** Wave 3 — weekly digest notification dispatched by the DigestJob. */
  WEEKLY_DIGEST = "notification.weekly_digest",
}

/** Aggregate types that events are associated with */
export enum AggregateType {
  WEEKLY_PLAN = "WeeklyPlan",
  WEEKLY_COMMIT = "WeeklyCommit",
  MANAGER_REVIEW = "ManagerReview",
}

/** Base outbox event envelope */
export interface OutboxEvent {
  eventId: string;
  eventType: EventType;
  aggregateType: AggregateType;
  aggregateId: string;
  orgId: string;
  payload: Record<string, unknown>;
  schemaVersion: number;
  occurredAt: string;
  publishedAt: string | null;
}

/** Notification types (MVP in-app only) */
export enum NotificationType {
  PLAN_STILL_DRAFT = "PLAN_STILL_DRAFT",
  PLAN_STILL_LOCKED = "PLAN_STILL_LOCKED",
  RECONCILIATION_OVERDUE = "RECONCILIATION_OVERDUE",
  RECONCILIATION_SUBMITTED = "RECONCILIATION_SUBMITTED",
  CHANGES_REQUESTED = "CHANGES_REQUESTED",
  /** Wave 3 — weekly digest summary sent to managers. */
  WEEKLY_DIGEST = "WEEKLY_DIGEST",
}
