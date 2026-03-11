Problem Statement

The organization currently uses 15-Five for weekly planning, but it has no structural connection between individual weekly commitments and organizational strategic goals. Managers lack visibility into how team members' weekly work maps to Rally Cries, Defining Objectives, and Outcomes, making it impossible to identify misalignment until it's too late. The challenge is to build a production-ready micro-frontend module that replaces 15-Five with a system that enforces this connection through a complete weekly lifecycle: commit entry, prioritization, reconciliation, and manager review.

Functional Requirements

Weekly commit CRUD with RCDO hierarchy linking, chess layer for categorization and prioritization, full weekly lifecycle state machine (DRAFT → LOCKED → RECONCILING → RECONCILED → Carry Forward), reconciliation view comparing planned vs. actual, manager dashboard with team roll-up, micro-frontend integration into existing PA host app following the PM remote pattern

Required Languages

TypeScript (strict mode), Java 21, SQL
