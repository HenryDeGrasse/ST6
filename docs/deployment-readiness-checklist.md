# Deployment Readiness Checklist

> Per PRD §14.10: Every item must be explicitly verified and signed off before MVP production launch.

## Infrastructure

- [ ] All production infrastructure provisioned via IaC (Terraform in `infra/terraform/`)
- [ ] Multi-AZ deployment confirmed (ECS tasks in ≥ 2 AZs, RDS Multi-AZ, Redis replica)
- [ ] TLS enabled on all connections (API, DB, Redis, SQS, LLM)
- [ ] Secrets stored in Secrets Manager (no secrets in code or config files)
- [ ] Network segmentation verified: no public IPs on workloads, egress via NAT Gateway

## Database

- [ ] All Flyway migrations applied cleanly (fresh DB and incremental from V001)
- [ ] RLS policies active on all tenant-scoped tables (verified by integration test)
- [ ] Automated backups configured (daily snapshots, continuous WAL, cross-region copy)
- [ ] Backup restore drill completed within RTO target

## Observability

- [ ] All dashboards deployed (`infra/observability/grafana-*.json`)
- [ ] All alerts configured and routed (`infra/observability/alert-rules.yml`)
- [ ] Structured logging confirmed: `correlationId` and `orgId` in all log entries
- [ ] SLO burn-rate alerts configured and tested with synthetic error injection
- [ ] Prometheus metrics endpoint active (`/actuator/prometheus`)

## Security

- [ ] JWT validation tested: expired → 401, wrong org → 403, missing role → 403
- [ ] Cross-tenant isolation verified: RLS, application `org_id` enforcement, cache key namespacing
- [ ] Container image scanned with zero critical CVEs
- [ ] SAST scan clean (no high/critical findings)
- [ ] Dependency vulnerability scan clean

## CI/CD

- [ ] All CI gates passing on `main` (lint, typecheck, unit, build)
- [ ] Canary deployment tested in staging
- [ ] Rollback procedure tested (`scripts/rollback.sh`)
- [ ] Docker image builds successfully from `backend/weekly-service/Dockerfile`

## On-Call

- [ ] On-call rotation configured with ≥ 2 engineers
- [ ] Escalation path documented and tested
- [ ] All runbooks written and reviewed (`docs/runbooks/`)

## Feature Flags

- [ ] All flags configured (`infra/flags/feature-flags.json`)
- [ ] Phase 0 rollout targeting configured (ST6 org only)

## Performance

- [ ] Load test completed: 50 concurrent users, 200 req/s sustained
- [ ] Manager dashboard tested with 50-user team, p95 < 500ms

## AI

- [ ] LLM provider key provisioned for production
- [ ] Rate limits and cost caps configured
- [ ] AI fallback tested: LLM disabled → graceful degradation

## Documentation

- [ ] API documentation (OpenAPI spec) published
- [ ] Architecture diagrams reviewed and accurate
- [ ] Runbooks cover all alert scenarios

## Stakeholder

- [ ] Product owner reviewed §18 acceptance criteria as complete
- [ ] QA completed manual exploratory testing on staging
- [ ] No P1/P2 bugs open
