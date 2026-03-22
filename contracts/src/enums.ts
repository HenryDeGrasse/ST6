/** Plan lifecycle state */
export enum PlanState {
  DRAFT = "DRAFT",
  LOCKED = "LOCKED",
  RECONCILING = "RECONCILING",
  RECONCILED = "RECONCILED",
  CARRY_FORWARD = "CARRY_FORWARD",
}

/** Manager review status (orthogonal to plan state) */
export enum ReviewStatus {
  REVIEW_NOT_APPLICABLE = "REVIEW_NOT_APPLICABLE",
  REVIEW_PENDING = "REVIEW_PENDING",
  CHANGES_REQUESTED = "CHANGES_REQUESTED",
  APPROVED = "APPROVED",
}

/** Chess-layer prioritization */
export enum ChessPriority {
  KING = "KING",
  QUEEN = "QUEEN",
  ROOK = "ROOK",
  BISHOP = "BISHOP",
  KNIGHT = "KNIGHT",
  PAWN = "PAWN",
}

/** Reconciliation completion status */
export enum CompletionStatus {
  DONE = "DONE",
  PARTIALLY = "PARTIALLY",
  NOT_DONE = "NOT_DONE",
  DROPPED = "DROPPED",
}

/** How the plan was locked */
export enum LockType {
  ON_TIME = "ON_TIME",
  LATE_LOCK = "LATE_LOCK",
}

/** Commitment categories */
export enum CommitCategory {
  DELIVERY = "DELIVERY",
  OPERATIONS = "OPERATIONS",
  CUSTOMER = "CUSTOMER",
  GTM = "GTM",
  PEOPLE = "PEOPLE",
  LEARNING = "LEARNING",
  TECH_DEBT = "TECH_DEBT",
}

// ─── Phase 6: Issue Backlog, Teams & AI Work Intelligence ───

/** Classifies the type of effort an issue represents */
export enum EffortType {
  BUILD = "BUILD",
  MAINTAIN = "MAINTAIN",
  COLLABORATE = "COLLABORATE",
  LEARN = "LEARN",
}

/** Lifecycle status for backlog issues */
export enum IssueStatus {
  OPEN = "OPEN",
  IN_PROGRESS = "IN_PROGRESS",
  DONE = "DONE",
  ARCHIVED = "ARCHIVED",
}

/** Role of a user within a team */
export enum TeamRole {
  OWNER = "OWNER",
  MEMBER = "MEMBER",
}

/** Status of a team access request */
export enum AccessRequestStatus {
  PENDING = "PENDING",
  APPROVED = "APPROVED",
  DENIED = "DENIED",
}

/** All audit activity types that can be recorded on an issue */
export enum IssueActivityType {
  CREATED = "CREATED",
  STATUS_CHANGE = "STATUS_CHANGE",
  ASSIGNMENT_CHANGE = "ASSIGNMENT_CHANGE",
  PRIORITY_CHANGE = "PRIORITY_CHANGE",
  EFFORT_TYPE_CHANGE = "EFFORT_TYPE_CHANGE",
  ESTIMATE_CHANGE = "ESTIMATE_CHANGE",
  COMMENT = "COMMENT",
  TIME_ENTRY = "TIME_ENTRY",
  OUTCOME_CHANGE = "OUTCOME_CHANGE",
  COMMITTED_TO_WEEK = "COMMITTED_TO_WEEK",
  RELEASED_TO_BACKLOG = "RELEASED_TO_BACKLOG",
  CARRIED_FORWARD = "CARRIED_FORWARD",
  BLOCKED = "BLOCKED",
  UNBLOCKED = "UNBLOCKED",
  DESCRIPTION_CHANGE = "DESCRIPTION_CHANGE",
  TITLE_CHANGE = "TITLE_CHANGE",
}
