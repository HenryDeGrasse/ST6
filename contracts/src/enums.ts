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
