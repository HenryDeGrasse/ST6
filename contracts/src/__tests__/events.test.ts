import { describe, it, expect } from "vitest";
import { EventType, AggregateType, NotificationType } from "../events.js";

describe("EventType enum", () => {
  it("contains all PRD Appendix B event types", () => {
    expect(EventType.PLAN_CREATED).toBe("plan.created");
    expect(EventType.PLAN_LOCKED).toBe("plan.locked");
    expect(EventType.PLAN_RECONCILIATION_STARTED).toBe("plan.reconciliation_started");
    expect(EventType.PLAN_RECONCILED).toBe("plan.reconciled");
    expect(EventType.PLAN_CARRY_FORWARD).toBe("plan.carry_forward");
    expect(EventType.REVIEW_SUBMITTED).toBe("review.submitted");
    expect(EventType.COMMIT_CREATED).toBe("commit.created");
    expect(EventType.COMMIT_UPDATED).toBe("commit.updated");
    expect(EventType.COMMIT_DELETED).toBe("commit.deleted");
    expect(EventType.COMMIT_ACTUAL_UPDATED).toBe("commit.actual_updated");
  });

  it("has 11 event types total (including Wave 3 WEEKLY_DIGEST)", () => {
    expect(Object.values(EventType)).toHaveLength(11);
  });

  it("contains WEEKLY_DIGEST with correct value", () => {
    expect(EventType.WEEKLY_DIGEST).toBe("notification.weekly_digest");
  });

  it("uses dot-delimited naming convention", () => {
    for (const value of Object.values(EventType)) {
      expect(value).toMatch(/^[a-z]+\.[a-z_]+$/);
    }
  });
});

describe("AggregateType enum", () => {
  it("contains the three aggregate types", () => {
    expect(AggregateType.WEEKLY_PLAN).toBe("WeeklyPlan");
    expect(AggregateType.WEEKLY_COMMIT).toBe("WeeklyCommit");
    expect(AggregateType.MANAGER_REVIEW).toBe("ManagerReview");
  });

  it("has exactly 3 types", () => {
    expect(Object.values(AggregateType)).toHaveLength(3);
  });
});

describe("NotificationType enum", () => {
  it("contains all MVP notification triggers from PRD §4", () => {
    expect(NotificationType.PLAN_STILL_DRAFT).toBe("PLAN_STILL_DRAFT");
    expect(NotificationType.PLAN_STILL_LOCKED).toBe("PLAN_STILL_LOCKED");
    expect(NotificationType.RECONCILIATION_OVERDUE).toBe("RECONCILIATION_OVERDUE");
    expect(NotificationType.RECONCILIATION_SUBMITTED).toBe("RECONCILIATION_SUBMITTED");
    expect(NotificationType.CHANGES_REQUESTED).toBe("CHANGES_REQUESTED");
  });

  it("contains WEEKLY_DIGEST added in Wave 3", () => {
    expect(NotificationType.WEEKLY_DIGEST).toBe("WEEKLY_DIGEST");
  });

  it("has 6 notification types", () => {
    expect(Object.values(NotificationType)).toHaveLength(6);
  });
});
