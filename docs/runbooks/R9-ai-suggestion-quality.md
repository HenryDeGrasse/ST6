# R9: AI Suggestion Quality Degradation

**Last reviewed:** 2026-03-13  
**Triggered by:** `WC_AI_SuggestionAcceptRate_Low` (P3), `WC_AI_ValidationRejectRate_High` (P3)

## Impact

AI-powered features return low-quality or malformed suggestions. Users
see irrelevant RCDO suggestions, poor reconciliation drafts, or empty
manager insights. No workflow impact — all AI features degrade to manual
input when suggestions are unusable.

## Triage steps

1. **Check acceptance rate.**
   - Grafana → WC AI Operations → suggestion accept/reject ratio
   - Baseline: ~40-60% acceptance rate
   - If acceptance drops below 20%, quality has degraded

2. **Check validation rejection rate.**
   - Grafana → WC AI Operations → `ResponseValidator` rejection rate
   - The `ResponseValidator` ensures LLM output matches the expected JSON schema
   - If rejection rate > 30%, the model is producing malformed output

3. **Check for model changes upstream.**
   - Did the LLM provider deprecate or change the model version?
   - Check OpenRouter / Anthropic status and changelog
   - Verify `ai.model` config still points to an active model

4. **Inspect recent LLM responses.**
   - Filter logs by `"component":"ResponseValidator"` and `"level":"WARN"`
   - Look for:
     - JSON parse failures → model returning non-JSON
     - Schema validation failures → model returning wrong structure
     - Empty responses → model timeout or refusal

5. **Check prompt integrity.**
   - Was `PromptBuilder` changed in a recent deploy?
   - Compare current system prompt to the last known-good version
   - Check if RCDO tree data (context window) has grown beyond model limits

6. **Check candidate selection.**
   - `CandidateSelector` filters the RCDO tree to relevant candidates
   - If the RCDO tree structure changed (new rally cries, renamed outcomes),
     the selector may be producing poor context

7. **Check cache behavior.**
   - `AiCacheService` caches suggestions with a TTL
   - If cache is serving stale suggestions after RCDO changes, clear cache:
     ```bash
     redis-cli KEYS "ai:cache:*" | xargs redis-cli DEL
     ```

## Mitigation

- **If model is producing malformed output:**
  - Switch to a different model: update `ai.model` config
  - Or disable AI temporarily: `wc.ai.suggest=false`

- **If prompt has regressed:**
  - Roll back the prompt change
  - Redeploy with the previous `PromptBuilder` version

- **If RCDO context is too large:**
  - Reduce `CandidateSelector` max candidates
  - Increase token limit in `AiConfiguration`

- **If cache is stale:**
  ```bash
  redis-cli KEYS "ai:cache:suggest:*" | xargs redis-cli DEL
  redis-cli KEYS "ai:cache:reconciliation:*" | xargs redis-cli DEL
  redis-cli KEYS "ai:cache:insights:*" | xargs redis-cli DEL
  ```

## Quality monitoring

Key metrics to track over time:
- **Accept rate:** % of AI suggestions accepted by users (target: > 40%)
- **Reject rate:** % of raw LLM responses rejected by `ResponseValidator` (target: < 10%)
- **Latency:** p95 of AI request duration (target: < 3s for suggest, < 5s for draft)
- **Fallback rate:** % of requests that fell back to non-AI path (target: < 5%)

## Prevention

- Pin model versions (avoid auto-upgrades)
- Add golden-file tests for `PromptBuilder` output
- Monitor accept rate weekly; investigate any 2-week declining trend
- Keep prompt token count under 80% of model context window
