# OpenAPI Fragments

Wave 1 agents write their endpoint definitions here as standalone YAML files.
Wave 2 Agent E merges them into the main `contracts/openapi.yaml`.

| File | Agent | Phase |
|------|-------|-------|
| `agent-a-quick-update.yaml` | Agent A | Phase 1: Quick Update + User Model |
| `agent-b-analytics.yaml` | Agent B | Phase 2: Multi-Week Strategic Intelligence |
| `agent-c-urgency.yaml` | Agent C | Phase 3: RCDO Target Dates + Urgency |
| `agent-d-capacity.yaml` | Agent D | Phase 4: Capacity Planning |

Each fragment should define paths, schemas, and request/response bodies
for that agent's endpoints. Use OpenAPI 3.1 format matching the main spec.
